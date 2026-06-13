package com.worldcretornica.plotme_core.api;

import com.worldcretornica.configuration.ConfigAccessor;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.block.Biome;
import org.bukkit.configuration.ConfigurationSection;

import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

public abstract class IServerBridge {

    private final Logger logger;


    protected IServerBridge(Logger bridgeLogger) {
        logger = bridgeLogger;
    }

    public abstract IOfflinePlayer getOfflinePlayer(UUID uuid);

    public abstract IOfflinePlayer getOfflinePlayer(String string);

    /**
     * Gets the player from the given UUID.
     *
     * @param uuid UUID of the player to retrieve
     * @return a player if one was found, null otherwise
     */
    public abstract IPlayer getPlayer(UUID uuid);

    public abstract IPlayer getPlayer(String name);

    public abstract Collection<IPlayer> getOnlinePlayers();

    public Logger getLogger() {
        return logger;
    }

    public abstract int runTaskTimerAsynchronously(Runnable task, long delay, long period);

    public abstract int scheduleSyncRepeatingTask(Runnable func, long delay, long period);

    public abstract void cancelTask(int taskId);

    public abstract void scheduleSyncDelayedTask(Runnable task, int i);

    public abstract void setupHooks();

    /**
     * Refresh any platform-specific web-map markers (BlueMap, squaremap, etc).
     * Default is a no-op so non-Bukkit platforms don't need to care.
     */
    public void refreshWebMapMarkers() {}

    public abstract Optional<Economy> getEconomy();

    /**
     * Gets balance of a player
     *
     * @param player of the player
     * @param price
     * @return Amount currently held in players account
     */
    public abstract boolean has(IPlayer player, double price);

    public abstract EconomyResponse withdrawPlayer(IPlayer player, double price);

    public abstract EconomyResponse depositPlayer(IOfflinePlayer currentBidder, double currentBid);

    public abstract void runTaskAsynchronously(Runnable runnable);

    public abstract void runTaskLaterAsynchronously(Runnable runnable, long delay);

    /**
     * Resolve a user-typed biome name against the runtime registry.
     * Accepts legacy forms ("PLAINS", "plains", "Birch Forest") and full
     * namespaced keys ("minecraft:birch_forest"). Returns the canonical key
     * path (e.g. "birch_forest") on success.
     */
    @SuppressWarnings("deprecation")
    public Optional<String> getBiome(String name) {
        if (name == null || name.isEmpty()) return Optional.empty();
        String raw = name.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
        NamespacedKey key = raw.contains(":")
                ? NamespacedKey.fromString(raw)
                : NamespacedKey.minecraft(raw);
        if (key == null) return Optional.empty();
        Registry<Biome> reg = Bukkit.getRegistry(Biome.class);
        if (reg == null) return Optional.empty();
        Biome biome = reg.get(key);
        return biome == null ? Optional.empty() : Optional.of(biome.getKey().getKey());
    }

    public abstract File getDataFolder();

    /**
     * All biome keys registered on the running server (post-1.13 names, no
     * typos, no duplicates — pulled live from Paper's Biome registry).
     */
    @SuppressWarnings("deprecation")
    public List<String> getBiomes() {
        Registry<Biome> reg = Bukkit.getRegistry(Biome.class);
        if (reg == null) return List.of();
        return StreamSupport.stream(reg.spliterator(), false)
                .map(b -> b.getKey().getKey())
                .sorted()
                .collect(Collectors.toList());
    }

    public abstract int runTask(Runnable task);

    public abstract int runTaskTimer(Runnable task, long period, long delay);

    /**
     * Get all Existing Plotworlds.
     * @return all plotworlds on the server
     */
    public abstract Collection<IWorld> getWorlds();

    public ConfigurationSection loadDefaultConfig(ConfigAccessor configFile, String world) {
        ConfigurationSection defaultWorld = getDefaultWorld();
        ConfigurationSection configSection;
        if (configFile.getConfig().contains(world)) {
            configSection = configFile.getConfig().getConfigurationSection(world);
        } else {
            configFile.getConfig().set(world, defaultWorld);
            configFile.saveConfig();
            configSection = configFile.getConfig().getConfigurationSection(world);
        }
        for (String path : defaultWorld.getKeys(true)) {
            configSection.addDefault(path, defaultWorld.get(path));
        }
        configFile.saveConfig();
        return configSection;
    }

    public abstract ConfigurationSection getDefaultWorld();

    public abstract File getWorldFolder();

    public abstract void runTaskLater(Runnable runnable, long delay);
}