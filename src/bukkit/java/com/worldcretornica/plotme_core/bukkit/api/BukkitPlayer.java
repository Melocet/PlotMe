package com.worldcretornica.plotme_core.bukkit.api;

import com.worldcretornica.plotme_core.PlotMe_Core;
import com.worldcretornica.plotme_core.TeleportRunnable;
import com.worldcretornica.plotme_core.api.IItemStack;
import com.worldcretornica.plotme_core.api.IPlayer;
import com.worldcretornica.plotme_core.api.IWorld;
import com.worldcretornica.plotme_core.api.Location;
import com.worldcretornica.plotme_core.api.Vector;
import com.worldcretornica.plotme_core.bukkit.BukkitUtil;
import org.bukkit.entity.Player;

public class BukkitPlayer extends BukkitOfflinePlayer implements IPlayer {

    private final Player player;

    public BukkitPlayer(Player player) {
        super(player);
        this.player = player;
    }

    /**
     * Sends this sender a message.
     *
     * <p><b>Prefix contract:</b> this method is the canonical chat boundary
     * for player-bound messages from PlotMe. It automatically prepends a
     * "[PlotMe]" tag to every line of {@code message}. Call sites MUST NOT
     * add their own "[PlotMe]" prefix -- {@link PlotMessagePrefix#apply} is
     * idempotent (skips already-prefixed input) but the wider codebase should
     * stay clean. If the cached {@code use-legacy-texts} flag is set the
     * prefix is rendered plain ("[PlotMe] "); otherwise it uses the colored
     * variant "§6[§ePlotMe§6]§r ". Multi-line messages get the prefix on
     * every line, not just the first.
     *
     * @param message Message to be displayed
     */

    @Override
    public void sendMessage(String message) {
        player.sendMessage(PlotMessagePrefix.apply(message));
    }

    @Override
    public void playSound(String soundName, float volume, float pitch) {
        if (soundName == null || soundName.isEmpty()) return;
        // Paper 1.21+ accepts the raw namespaced/colon key in the String overload,
        // which avoids the deprecated Sound enum lookup entirely.
        try {
            player.playSound(player.getLocation(), soundName.toLowerCase().replace('_', '.'), volume, pitch);
        } catch (Throwable ignored) {
            // Unknown sound key — silently skip rather than crash the command.
        }
    }

    @Override
    public boolean hasPermission(String permission) {
        return player.hasPermission(permission);
    }

    @Override
    public IWorld getWorld() {
        return new BukkitWorld(player.getWorld());
    }

    @Override
    public Location getLocation() {
        return new Location(getWorld(), getPosition());
    }

    @Override
    public void setLocation(Location location) {
        player.teleport(BukkitUtil.adapt(location));
    }

    /**
     * Uses the code that allows a delay while
     * "Teleporting" or moving the entity
     *
     * @param location new location
     * @param plugin
     */
    @Override public void teleport(Location location, PlotMe_Core plugin) {
        if (plugin.getConfig().getInt("tp-delay") != 0) {
            // Route through our own sendMessage so this message also gets the
            // canonical "[PlotMe]" prefix -- otherwise the tp-delay notice
            // would be the one chat string in the plugin without the tag.
            sendMessage(String.format("§eYou will be teleported in §b%d§e seconds.", plugin.getConfig().getInt("tp-delay")));
            plugin.getServerBridge().runTaskLater(new TeleportRunnable(this, location), plugin.getConfig().getInt("tp-delay"));
        } else {
            setLocation(location);
        }
    }

    public Player getPlayer() {
        return player;
    }

    @Override
    public IItemStack getItemInHand() {
        return new BukkitItemStack(player.getItemInHand());
    }

    @Override
    public void remove() {
        // Players cannot be removed via Entity#remove() in modern Paper — it
        // throws UnsupportedOperationException. Plot operations should never
        // be removing the player anyway, so this is intentionally a no-op.
    }

    @Override
    public String toString() {
        return "Bukkit Player{ name= " + getName() + " uuid = " + getUniqueId().toString() + " }";
    }

    public Vector getPosition() {
        return BukkitUtil.locationToVector(player.getLocation());
    }
}
