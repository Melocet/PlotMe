package com.worldcretornica.plotme_core.bukkit;

import com.worldcretornica.plotme.defaultgenerator.DefaultChunkGenerator;
import com.worldcretornica.plotme.defaultgenerator.DefaultPlotManager;
import com.worldcretornica.plotme.defaultgenerator.DefaultWorldConfigPath;
import com.worldcretornica.plotme_core.PlotMeCoreManager;
import com.worldcretornica.plotme_core.PlotMe_Core;
import com.worldcretornica.plotme_core.api.IEntity;
import com.worldcretornica.plotme_core.api.IPlayer;
import com.worldcretornica.plotme_core.api.IPlotMe_GeneratorManager;
import com.worldcretornica.plotme_core.api.IServerBridge;
import com.worldcretornica.plotme_core.api.IWorld;
import com.worldcretornica.plotme_core.bukkit.api.BukkitEntity;
import com.worldcretornica.plotme_core.bukkit.api.BukkitPlayer;
import com.worldcretornica.plotme_core.bukkit.api.BukkitWorld;
import com.worldcretornica.plotme_core.bukkit.listener.BukkitPlotDenyListener;
import com.worldcretornica.plotme_core.bukkit.listener.BukkitPlotListener;
import com.worldcretornica.plotme_core.bukkit.listener.PlotMenuListener;
import org.bstats.bukkit.Metrics;
import org.bstats.charts.SingleLineChart;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldInitEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.UUID;

public class PlotMe_CorePlugin extends JavaPlugin implements Listener {

    private static PlotMe_CorePlugin INSTANCE;
    private final HashMap<UUID, BukkitPlayer> bukkitPlayerMap = new HashMap<>();
    private final PlotMe_Core plotme = new PlotMe_Core();
    private BukkitServerBridge serverObjectBuilder;

    public static PlotMe_CorePlugin getInstance() {
        return INSTANCE;
    }

    @Override
    public void onDisable() {
        getAPI().disable();
        getBukkitPlayerMap().clear();
    }

    @Override
    public void onEnable() {
        INSTANCE = this;
        getLogger().info("Enabling PlotMe...Waiting for generator data.");
        serverObjectBuilder = new BukkitServerBridge(this, getLogger());
        plotme.registerServerBridge(serverObjectBuilder);
        getAPI().enable();
        doMetric();

        //Register Bukkit Events
        PluginManager pm = getServer().getPluginManager();
        BukkitPlotListener listener = new BukkitPlotListener(this);
        pm.registerEvents(listener, this);
        pm.registerEvents(new BukkitPlotDenyListener(this), this);
        pm.registerEvents(new PlotMenuListener(this), this);
        pm.registerEvents(this, this);
        plotme.getEventBus().register(listener);
        //Register Command
        this.getCommand("plotme").setExecutor(new BukkitCommand(this));

        // Re-register any worlds that were already loaded before this plugin
        // initialised (Multiverse/MultiWorld may finish world load on STARTUP
        // before our onEnable runs).
        for (World w : getServer().getWorlds()) {
            if (w.getGenerator() instanceof DefaultChunkGenerator) {
                registerPlotWorld(w);
            }
        }
    }

    @Override
    public ChunkGenerator getDefaultWorldGenerator(String worldName, String id) {
        // Bukkit calls this BEFORE the World object exists, so we cannot
        // register the gen manager into PlotMe_Core.managers here (that map
        // is keyed by IWorld, not by name).
        //
        // We DO ensure the per-world config section exists right now so
        // that any later registration path uses consistent values.
        //
        // Registration into the gen-manager map happens in onWorldInit /
        // onWorldLoad below, both of which fire synchronously inside
        // Bukkit's WorldCreator#createWorld() chain. By the time `/mv
        // create plots normal -g PlotMe` returns, the gen manager should
        // therefore be registered. We also schedule a next-tick safety
        // sweep below in case a Multiverse build path swallows one of
        // those events on a particular server flavour.
        ConfigurationSection wgc = ensureWorldConfig(worldName);
        final String pendingName = worldName;
        getServer().getScheduler().runTask(this, () -> {
            World w = getServer().getWorld(pendingName);
            if (w != null && w.getGenerator() instanceof DefaultChunkGenerator) {
                registerPlotWorld(w);
            }
        });
        return new DefaultChunkGenerator(wgc);
    }

    @EventHandler
    public void onWorldInit(WorldInitEvent event) {
        // Primary registration point: fires synchronously while the world
        // is still being built inside WorldCreator#createWorld. This is
        // what makes `/plotme auto` immediately after `/mv create` safe
        // on the happy path.
        if (event.getWorld().getGenerator() instanceof DefaultChunkGenerator) {
            registerPlotWorld(event.getWorld());
        }
    }

    @EventHandler
    public void onWorldLoad(WorldLoadEvent event) {
        // Belt + suspenders: catches worlds that already existed at
        // server boot (re-load from disk) or that some Multiverse
        // variants only surface via WorldLoadEvent rather than
        // WorldInitEvent. registerPlotWorld is idempotent.
        if (event.getWorld().getGenerator() instanceof DefaultChunkGenerator) {
            registerPlotWorld(event.getWorld());
        }
    }

    private void registerPlotWorld(World w) {
        BukkitWorld bw = new BukkitWorld(w);
        if (plotme.getGenManager(bw) != null) {
            return; // already registered
        }
        ConfigurationSection wgc = ensureWorldConfig(w.getName());
        getLogger().info("Registering PlotMe world: " + w.getName());
        plotme.addManager(bw, new DefaultPlotManager(wgc, bw));
    }

    private ConfigurationSection ensureWorldConfig(String worldName) {
        String key = "worlds." + worldName.toLowerCase();
        ConfigurationSection wgc = plotme.getConfig().getConfigurationSection(key);
        if (wgc == null) {
            wgc = plotme.getConfig().createSection(key);
        }
        for (DefaultWorldConfigPath p : DefaultWorldConfigPath.values()) {
            if (!wgc.contains(p.key())) {
                wgc.set(p.key(), p.value());
            }
        }
        return wgc;
    }

    public PlotMe_Core getAPI() {
        return plotme;
    }

    public IServerBridge getServerObjectBuilder() {
        return serverObjectBuilder;
    }

    private void doMetric() {
        // bStats plugin ID for PlotMe-Core (modern fork).
        Metrics metrics = new Metrics(this, 24000);
        final PlotMeCoreManager manager = PlotMeCoreManager.getInstance();

        metrics.addCustomChart(new SingleLineChart("plotworlds",
                () -> manager.getPlotMaps().size()));

        metrics.addCustomChart(new SingleLineChart("average_plot_size", () -> {
            if (manager.getPlotMaps().isEmpty()) {
                return 0;
            }
            int totalPlotSize = 0;
            for (IWorld plotter : manager.getPlotMaps().keySet()) {
                IPlotMe_GeneratorManager genmanager = plotme.getGenManager(plotter);
                if (genmanager != null) {
                    totalPlotSize += genmanager.getPlotSize();
                }
            }
            return totalPlotSize / manager.getPlotMaps().size();
        }));

        metrics.addCustomChart(new SingleLineChart("number_of_plots",
                () -> getAPI().getSqlManager().getTotalPlotCount()));
    }


    /**
     * Gets a cache of BukkitPlayers for use in commands. Reducing the number of BukkitPlayer Objects being created. Players are removed on logoff.
     * @param player {@link Player} from Bukkit
     * @return a BukkitPlayer for the player given
     */
    public IPlayer wrapPlayer(Player player) {
        if (bukkitPlayerMap.containsKey(player.getUniqueId())) {
            return bukkitPlayerMap.get(player.getUniqueId());
        } else {
            BukkitPlayer bukkitplayer = new BukkitPlayer(player);
            bukkitPlayerMap.put(player.getUniqueId(), bukkitplayer);
            return bukkitplayer;
        }
    }

    public IEntity wrapEntity(Entity entity) {
        if (entity instanceof Player) {
            return wrapPlayer((Player) entity);
        }
        return new BukkitEntity(entity);
    }

    public void removePlayer(UUID playerUUID) {
        bukkitPlayerMap.remove(playerUUID);
    }

    public HashMap<UUID, BukkitPlayer> getBukkitPlayerMap() {
        return bukkitPlayerMap;
    }


}
