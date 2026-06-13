package com.worldcretornica.plotme_core;

import com.worldcretornica.configuration.ConfigAccessor;
import com.worldcretornica.plotme_core.api.ICommandSender;
import com.worldcretornica.plotme_core.api.IPlotMe_GeneratorManager;
import com.worldcretornica.plotme_core.api.IServerBridge;
import com.worldcretornica.plotme_core.api.IWorld;
import com.worldcretornica.plotme_core.api.event.eventbus.EventBus;
import com.worldcretornica.plotme_core.storage.Database;
import com.worldcretornica.plotme_core.storage.MySQLConnector;
import com.worldcretornica.plotme_core.storage.SQLiteConnector;
import com.worldcretornica.plotme_core.utils.ClearEntry;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.text.MessageFormat;
import java.util.HashMap;
import java.util.MissingResourceException;
import java.util.ResourceBundle;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

public class PlotMe_Core {

    private final HashMap<IWorld, IPlotMe_GeneratorManager> managers = new HashMap<>();
    private final EventBus eventBus = new EventBus();
    //Bridge
    private IServerBridge serverBridge;
    private IWorld worldcurrentlyprocessingexpired;
    private int counterExpired;
    private Database sqlManager;
    //Caption and Config File.
    private ConfigAccessor configFile;
    private ResourceBundle captions;
    private int spoolTaskId = -1;
    private int expireTaskId = -1;
    // Cached 'use-legacy-texts' flag. When true, the C() helper strips Bukkit
    // color codes from resolved captions (pre-color legacy behavior). When
    // false (default), '&' codes are translated to '§' so chat renders colored.
    // Cached at enable/reload to avoid touching the config on every caption
    // lookup -- captions are hit on hot paths.
    private boolean useLegacyTexts;

    // Last-enabled instance so the chat-boundary wrappers (BukkitPlayer /
    // BukkitCommandSender) can read the cached legacy flag without holding
    // their own reference to the plugin. Written in enable() and reload().
    // Reads are best-effort: if it's null (very early bootstrap) the wrapper
    // falls back to the non-legacy colored prefix.
    private static volatile PlotMe_Core instance;

    /**
     * Returns the most recently enabled PlotMe_Core instance, or {@code null}
     * if the plugin hasn't fully booted yet. Used by the chat-send boundary
     * wrappers to read the cached {@code use-legacy-texts} flag.
     */
    public static PlotMe_Core getInstance() {
        return instance;
    }

    /**
     * Cached {@code use-legacy-texts} flag accessor. Safe to call from any
     * thread; the underlying field is set once at enable/reload and read
     * many times from chat send paths.
     */
    public boolean isUseLegacyTexts() {
        return useLegacyTexts;
    }

    // Matches Bukkit-style color/formatting codes after they've been resolved
    // to either '&' (raw caption) or '§' (already translated). Covers 0-9,
    // a-f (colors) and k-o, r (formatting). Used for the legacy strip path.
    private static final Pattern COLOR_CODE_PATTERN =
            Pattern.compile("[&§][0-9a-fk-orA-FK-OR]");


    public PlotMe_Core() {
    }

    public IPlotMe_GeneratorManager getGenManager(IWorld world) {
        return managers.get(world);
    }

    public void registerServerBridge(IServerBridge bridge) {
        serverBridge = bridge;
    }

    public void disable() {
        cancelScheduledTasks();
        // Stop the async flusher first so it can't fire one more tick against the
        // connection we're about to close.
        if (getSqlManager() != null) {
            getSqlManager().stopFlusher();
            // Final synchronous drain of any plot edits that arrived since the last
            // flush tick. Without this we'd lose them when the connection closes.
            getSqlManager().flushNow();
        }
        getSqlManager().closeConnection();
        PlotMeCoreManager.getInstance().getPlotMaps().clear();
        setWorldCurrentlyProcessingExpired(null);
        managers.clear();
        // Drop the static reference so a re-enabled plugin doesn't keep the
        // old (now-disabled) instance alive via the chat-send boundary.
        if (instance == this) {
            instance = null;
        }
    }

    public void enable() {
        // Publish ourselves to the static accessor BEFORE anything else so the
        // chat-send wrappers (BukkitPlayer / BukkitCommandSender) have access
        // to the cached legacy-texts flag if they fire during startup.
        instance = this;
        PlotMeCoreManager.getInstance().setPlugin(this);
        // Register the built-in per-plot flag set before anything that might
        // read flag values (config-driven init below, listeners registered
        // by the platform bridge). Safe to call repeatedly -- the registry
        // guards against double-registration internally.
        com.worldcretornica.plotme_core.flag.PlotFlagRegistry.ensureDefaultsRegistered();
        configFile = new ConfigAccessor(getServerBridge().getDataFolder(), "config.yml");
        captions = ResourceBundle.getBundle("messages");
        setupConfigFiles();
        // Cache the color-mode flag now that the config has been written/loaded.
        useLegacyTexts = getConfig().getBoolean("use-legacy-texts", false);
        setupSQL();
        serverBridge.setupHooks();
        // Spool runs every 2 ticks; the spool itself enforces a per-tick
        // block/time budget so this stays cheap even on big plots.
        spoolTaskId = serverBridge.runTaskTimer(new PlotMeSpool(this), 2, 2);
        if (getConfig().getBoolean("ExpirePlotCleanup")) {
            //20L * 60 = 1 minute in ticks
            expireTaskId = serverBridge
                    .runTaskTimerAsynchronously(new PlotExpireCleanup(this), 20L * 60 * 30, 20L * 60 * getConfig().getInt("ExpirePlotCleanupTimer"));
        }
        // Start the async dirty-plot flusher. Gameplay paths now call markDirty
        // (via savePlot) instead of doing per-event JDBC writes on the main thread;
        // this task drains the dirty set on a fixed cadence.
        getSqlManager().startFlusher();
    }

    public void reload() {
        cancelScheduledTasks();
        // Same shutdown order as disable(): stop the flusher, drain pending writes,
        // then close the connection. setupSQL() will hand us a fresh Database, and
        // the post-reload startFlusher() call below restarts the cadence.
        if (getSqlManager() != null) {
            getSqlManager().stopFlusher();
            getSqlManager().flushNow();
        }
        getSqlManager().closeConnection();
        setupConfigFiles();
        configFile.reloadFile();
        captions = ResourceBundle.getBundle("messages");
        // Re-cache the color-mode flag so /plotme reload picks up edits.
        useLegacyTexts = getConfig().getBoolean("use-legacy-texts", false);
        setupSQL();
        PlotMeCoreManager.getInstance().getPlotMaps().clear();

        for (IWorld world : managers.keySet()) {
            setupWorld(world);
        }

        spoolTaskId = serverBridge.runTaskTimer(new PlotMeSpool(this), 2, 2);
        if (getConfig().getBoolean("ExpirePlotCleanup")) {
            expireTaskId = serverBridge
                    .runTaskTimerAsynchronously(new PlotExpireCleanup(this), 20L * 60 * 30, 20L * 60 * getConfig().getInt("ExpirePlotCleanupTimer"));
        }
        // Restart the flusher against the freshly created Database instance.
        getSqlManager().startFlusher();

        // Re-sync web-map markers (BlueMap / squaremap). No-op when neither is installed.
        serverBridge.refreshWebMapMarkers();
    }

    private void cancelScheduledTasks() {
        if (spoolTaskId != -1) {
            serverBridge.cancelTask(spoolTaskId);
            spoolTaskId = -1;
        }
        if (expireTaskId != -1) {
            serverBridge.cancelTask(expireTaskId);
            expireTaskId = -1;
        }
    }

    public Logger getLogger() {
        return serverBridge.getLogger();
    }

    private void setupConfigFiles() {
        createConfigs();
        // Get the config we will be working with
        FileConfiguration config = getConfig();
        // Do any config validation
        if (config.getInt("NbClearSpools") > 20) {
            getLogger().warning("Having more than 20 clear spools seems drastic, changing to 20");
            config.set("NbClearSpools", 20);
        }
        //Check if the config doesn't have the worlds section. This should happen only if there is no config file for the plugin already.
        if (!config.contains("worlds")) {
            getServerBridge().loadDefaultConfig(configFile, "worlds.plotworld");
        }
        // Legacy "Version: 0.17.3" key was written here by old PlotMe. It is
        // unused by any current code path — explicitly drop it on load so it
        // disappears from saved configs.
        if (getConfig().contains("Version")) {
            getConfig().set("Version", null);
        }
        // Copy new values over
        getConfig().options().copyDefaults(true);
        configFile.saveConfig();
    }

    private void createConfigs() {
        if (configFile.createFile()) {
            getLogger().info("Created Config File");
        }
    }

    private void setupWorld(IWorld world) {
        getLogger().info("PlotMe is recieving data from the Generator");
        getServerBridge().loadDefaultConfig(configFile, "worlds." + world.getName().toLowerCase());
        PlotMapInfo pmi = new PlotMapInfo(configFile, world.getName().toLowerCase());
        PlotMeCoreManager.getInstance().addPlotMap(world, pmi);
        getSqlManager().loadPlotsAsynchronously(world);
    }

    /**
     * Setup SQL Database
     */
    private void setupSQL() {
        FileConfiguration config = getConfig();
        if (config.getBoolean("usemySQL", false)) {
            String url = config.getString("mySQLconn");
            String user = config.getString("mySQLuname");
            String pass = config.getString("mySQLpass");
            setSqlManager(new MySQLConnector(this, url, user, pass));
        } else {
            setSqlManager(new SQLiteConnector(this));
        }
    }

    /**
     * The point where the generator activates PlotMe
     */
    public void addManager(IWorld world, IPlotMe_GeneratorManager manager) {
        managers.put(world, manager);
        setupWorld(world);
    }

    public IPlotMe_GeneratorManager removeManager(IWorld world) {
        return managers.remove(world);
    }

    public void scheduleTask(Runnable task) {
        getLogger().info(this.C("MsgStartDeleteSession"));

        for (int ctr = 0; ctr < 10; ctr++) {
            serverBridge.scheduleSyncDelayedTask(task, ctr * 100);
        }
    }

    public String C(String caption, Object... args) {
        ResourceBundle bundle = captions;
        if (bundle == null) {
            bundle = ResourceBundle.getBundle("messages");
            captions = bundle;
        }

        String string;
        try {
            string = bundle.getString(caption);
        } catch (MissingResourceException ex) {
            return "[Missing caption \"" + caption + "\". Please report this to the author of PlotMe.]";
        }

        String formatted = MessageFormat.format(string, args);
        // Legacy mode: strip every Bukkit color/format code so output is plain.
        // We strip both '&' (un-translated source) and '§' (already-translated
        // sources like SignForSale='§9§lFOR SALE') in a single pass.
        if (useLegacyTexts) {
            return COLOR_CODE_PATTERN.matcher(formatted).replaceAll("");
        }
        // Default mode: translate '&' codes to '§' so chat renders in color.
        return ChatColor.translateAlternateColorCodes('&', formatted);
    }

    public IWorld getWorldCurrentlyProcessingExpired() {
        return worldcurrentlyprocessingexpired;
    }

    public void setWorldCurrentlyProcessingExpired(IWorld worldcurrentlyprocessingexpired) {
        this.worldcurrentlyprocessingexpired = worldcurrentlyprocessingexpired;
    }

    public int getCounterExpired() {
        return counterExpired;
    }

    public void setCounterExpired(int counterExpired) {
        this.counterExpired = counterExpired;
    }

    public void addPlotToClear(Plot plot, ClearReason reason, ICommandSender sender) {
        getLogger().log(Level.INFO, "plot to clear add {0}", plot.getId());
        PlotMeSpool.clearList.add(new ClearEntry(plot, reason, sender));
        if (sender != null) {
            sender.sendMessage("§eClearing Plot §b" + plot.getId().getID() + "§r");
        }
    }

    public boolean isPlotLocked(PlotId id) {
        if (PlotMeSpool.clearList.isEmpty()) {
            return false;
        } else {
            for (ClearEntry clearEntry : PlotMeSpool.clearList) {
                if (clearEntry.getPlot().getId().equals(id)) {
                    return true;
                }
            }

        }

        return false;
    }

    public IServerBridge getServerBridge() {
        return serverBridge;
    }

    public Database getSqlManager() {
        return sqlManager;
    }

    private void setSqlManager(Database sqlManager) {
        this.sqlManager = sqlManager;
    }

    public YamlConfiguration getConfig() {
        return configFile.getConfig();
    }

    public EventBus getEventBus() {
        return eventBus;
    }

}
