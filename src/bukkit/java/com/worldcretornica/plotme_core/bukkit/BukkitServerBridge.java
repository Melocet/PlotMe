package com.worldcretornica.plotme_core.bukkit;

import com.sk89q.worldedit.WorldEdit;
import com.worldcretornica.plotme_core.api.IOfflinePlayer;
import com.worldcretornica.plotme_core.api.IPlayer;
import com.worldcretornica.plotme_core.api.IServerBridge;
import com.worldcretornica.plotme_core.api.IWorld;
import com.worldcretornica.plotme_core.bukkit.api.BukkitOfflinePlayer;
import com.worldcretornica.plotme_core.bukkit.api.BukkitWorld;
import com.worldcretornica.plotme_core.bukkit.integration.WebMapDispatcher;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;

public class BukkitServerBridge extends IServerBridge {

    private final PlotMe_CorePlugin plotMeCorePlugin;
    private WebMapDispatcher webMapDispatcher;

    public BukkitServerBridge(PlotMe_CorePlugin plotMeCorePlugin, Logger logger) {
        super(logger);
        this.plotMeCorePlugin = plotMeCorePlugin;
    }

    /**
     * @return the web-map marker dispatcher, or {@code null} if {@link #setupHooks()}
     *         hasn't run yet. Used by {@link com.worldcretornica.plotme_core.PlotMe_Core#reload()}
     *         to repopulate markers after config changes.
     */
    public WebMapDispatcher getWebMapDispatcher() {
        return webMapDispatcher;
    }

    @Override
    public void refreshWebMapMarkers() {
        if (webMapDispatcher != null) {
            webMapDispatcher.refreshAll();
        }
    }

    @Override
    public IOfflinePlayer getOfflinePlayer(UUID uuid) {
        return new BukkitOfflinePlayer(Bukkit.getOfflinePlayer(uuid));
    }

    @Override public IOfflinePlayer getOfflinePlayer(String string) {
        OfflinePlayer player = Bukkit.getOfflinePlayer(string);
        if (player == null) {
            return null;
        } else {
            return new BukkitOfflinePlayer(player);
        }
    }

    @Override
    public int scheduleSyncRepeatingTask(Runnable func, long delay, long period) {
        return Bukkit.getScheduler().scheduleSyncRepeatingTask(plotMeCorePlugin, func, delay, period);
    }

    @Override
    public void cancelTask(int taskId) {
        Bukkit.getScheduler().cancelTask(taskId);
    }

    @Override
    public void scheduleSyncDelayedTask(Runnable task, int i) {
        Bukkit.getScheduler().scheduleSyncDelayedTask(plotMeCorePlugin, task, i);
    }

    /**
     * Setup PlotMe plugin hooks
     */
    @Override
    public void setupHooks() {
        PluginManager pluginManager = plotMeCorePlugin.getServer().getPluginManager();
        if (pluginManager.getPlugin("WorldEdit") != null) {
            WorldEdit.getInstance().getEventBus().register(new PlotWorldEditListener(plotMeCorePlugin.getAPI()));
        }

        // Web-map markers (BlueMap + squaremap). Each hook self-disables when
        // its backing plugin is missing — no version checks needed here.
        webMapDispatcher = new WebMapDispatcher(plotMeCorePlugin.getAPI(), getLogger());
        if (webMapDispatcher.anyAvailable()) {
            plotMeCorePlugin.getAPI().getEventBus().register(webMapDispatcher);
        }
    }

    /**
     * Get Economy from Vault
     * @return
     */
    @Override
    public Optional<Economy> getEconomy() {
        RegisteredServiceProvider<Economy> economyProvider = Bukkit.getServer().getServicesManager().getRegistration(Economy.class);
        if (economyProvider != null) {
            return Optional.of(economyProvider.getProvider());
        }
        return Optional.empty();
    }

    @Override
    public void runTaskAsynchronously(Runnable runnable) {
        Bukkit.getScheduler().runTaskAsynchronously(plotMeCorePlugin, runnable);
    }

    @Override
    public void runTaskLaterAsynchronously(Runnable runnable, long delay) {
        Bukkit.getScheduler().runTaskLaterAsynchronously(PlotMe_CorePlugin.getInstance(), runnable, delay);
    }

    @Override
    public IPlayer getPlayer(UUID uuid) {
        Player player = Bukkit.getPlayer(uuid);
        if (player == null) {
            return null;
        } else {
            return PlotMe_CorePlugin.getInstance().wrapPlayer(player);
        }
    }

    /**
     * Gets the player with the given name.
     *
     * @param playerName Player name
     * @return returns a an instance of IPlayer if found, otherwise null
     */
    @Override
    public IPlayer getPlayer(String playerName) {
        Player player = Bukkit.getPlayerExact(playerName);
        if (player == null) {
            return null;
        } else {
            return PlotMe_CorePlugin.getInstance().wrapPlayer(player);
        }
    }

    @Override
    public File getDataFolder() {
        return plotMeCorePlugin.getDataFolder();
    }

    @Override
    public boolean has(IPlayer player, double price) {
        if (getEconomy().isPresent()) {
            return getEconomy().get().has(((BukkitOfflinePlayer) player).getOfflinePlayer(), price);
        } else {
            return false;
        }
    }

    @Override
    public EconomyResponse withdrawPlayer(IPlayer player, double price) {
        if (getEconomy().isPresent()) {
            return getEconomy().get().withdrawPlayer(((BukkitOfflinePlayer) player).getOfflinePlayer(), price);
        } else {
            return null;
        }
    }

    @Override

    public EconomyResponse depositPlayer(IOfflinePlayer player, double price) {
        if (getEconomy().isPresent()) {
            return getEconomy().get().depositPlayer(((BukkitOfflinePlayer) player).getOfflinePlayer(), price);
        } else {
            return null;
        }
    }

    @Override
    public Collection<IPlayer> getOnlinePlayers() {
        Collection<IPlayer> players = new ArrayList<>();

        for (Player player : Bukkit.getOnlinePlayers()) {
            players.add(PlotMe_CorePlugin.getInstance().wrapPlayer(player));
        }

        return players;
    }

    @Override public int runTaskTimerAsynchronously(Runnable task, long delay, long period) {
        return Bukkit.getScheduler().runTaskTimerAsynchronously(plotMeCorePlugin, task, delay, period).getTaskId();
    }

    @Override public int runTask(Runnable task) {
        return Bukkit.getScheduler().runTask(plotMeCorePlugin, task).getTaskId();
    }

    @Override public int runTaskTimer(Runnable task, long period, long delay) {
        return Bukkit.getScheduler().runTaskTimer(plotMeCorePlugin, task, delay, period).getTaskId();
    }

    @Override
    public Collection<IWorld> getWorlds() {
        List<IWorld> worlds = new ArrayList<>();

        for (World world : Bukkit.getWorlds()) {
            worlds.add(new BukkitWorld(world));
        }

        return worlds;
    }

    public void clearBukkitPlayerMap() {
        PlotMe_CorePlugin.getInstance().getBukkitPlayerMap().clear();
    }

    public File getWorldFolder() {
        return plotMeCorePlugin.getServer().getWorldContainer();
    }

    public ConfigurationSection getDefaultWorld() {
        return org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(
                new InputStreamReader(getClass().getClassLoader().getResourceAsStream("default-world.yml"), StandardCharsets.UTF_8));
    }

    @Override public void runTaskLater(Runnable runnable, long delay) {
        Bukkit.getServer().getScheduler().runTaskLater(plotMeCorePlugin, runnable, delay);
    }
}
