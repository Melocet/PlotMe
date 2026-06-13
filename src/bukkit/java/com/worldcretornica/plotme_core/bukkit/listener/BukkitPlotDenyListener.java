package com.worldcretornica.plotme_core.bukkit.listener;

import com.worldcretornica.plotme_core.PermissionNames;
import com.worldcretornica.plotme_core.Plot;
import com.worldcretornica.plotme_core.PlotId;
import com.worldcretornica.plotme_core.PlotMapInfo;
import com.worldcretornica.plotme_core.PlotMeCoreManager;
import com.worldcretornica.plotme_core.api.IPlayer;
import com.worldcretornica.plotme_core.api.IWorld;
import com.worldcretornica.plotme_core.api.Location;
import com.worldcretornica.plotme_core.api.event.PlotEnterEvent;
import com.worldcretornica.plotme_core.api.event.PlotLeaveEvent;
import com.worldcretornica.plotme_core.bukkit.BukkitUtil;
import com.worldcretornica.plotme_core.bukkit.PlotMe_CorePlugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class BukkitPlotDenyListener implements Listener {

    private final PlotMe_CorePlugin plugin;
    private final PlotMeCoreManager manager;

    /**
     * Tracks the last plot a player was known to be standing on, keyed by
     * player UUID. A null value means the player was last seen outside any
     * plot (but still inside a plot world). Players outside plot worlds are
     * not tracked at all (entries are removed on world exit/quit).
     */
    private final Map<UUID, PlotId> lastPlot = new HashMap<>();

    public BukkitPlotDenyListener(PlotMe_CorePlugin plotMeCorePlugin) {
        plugin = plotMeCorePlugin;
        manager = PlotMeCoreManager.getInstance();
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerMove(PlayerMoveEvent event) {
        // Fast path: skip per-block-fraction moves. Only process when the
        // player crosses a whole block boundary on the X/Z axis. Y movement
        // alone can never change the plot.
        if (event.getFrom().getBlockX() == event.getTo().getBlockX()
                && event.getFrom().getBlockZ() == event.getTo().getBlockZ()) {
            return;
        }

        Location location = BukkitUtil.adapt(event.getTo());
        UUID playerId = event.getPlayer().getUniqueId();

        if (!manager.isPlotWorld(location)) {
            // Left a plot world (or never was in one). Fire a leave event for
            // the previous plot if there was one, then forget the player.
            PlotId previousId = lastPlot.remove(playerId);
            if (previousId != null) {
                IWorld fromWorld = BukkitUtil.adapt(event.getFrom()).getWorld();
                if (fromWorld != null && manager.isPlotWorld(fromWorld)) {
                    Plot previous = manager.getPlotById(previousId, fromWorld);
                    if (previous != null) {
                        plugin.getAPI().getEventBus().post(new PlotLeaveEvent(previous, plugin.wrapPlayer(event.getPlayer())));
                    }
                }
            }
            return;
        }

        PlotId currentId = manager.getPlotId(location);
        PlotId previousId = lastPlot.get(playerId);

        // Original deny-on-move behavior. Runs FIRST so that if the player is
        // bounced back to event.getFrom(), we don't fire a spurious
        // enter/leave for a plot they never actually stood on.
        if (!event.getPlayer().hasPermission(PermissionNames.ADMIN_BYPASSDENY)) {
            Plot denyPlot = currentId == null ? null : manager.getPlotById(currentId, location.getWorld());
            if (denyPlot != null
                    && !denyPlot.getOwnerId().equals(event.getPlayer().getUniqueId())
                    && denyPlot.isDenied(event.getPlayer().getUniqueId())) {
                event.setTo(event.getFrom());
                return;
            }
        }

        // No change in plot id (both null = still standing on the road, or
        // both the same plot = no boundary crossed). Nothing to fire.
        if ((currentId == null && previousId == null)
                || (currentId != null && currentId.equals(previousId))) {
            return;
        }

        IPlayer wrappedPlayer = plugin.wrapPlayer(event.getPlayer());

        if (previousId != null) {
            Plot previous = manager.getPlotById(previousId, location.getWorld());
            if (previous != null) {
                plugin.getAPI().getEventBus().post(new PlotLeaveEvent(previous, wrappedPlayer));
            }
        }

        if (currentId != null) {
            Plot current = manager.getPlotById(currentId, location.getWorld());
            if (current != null) {
                plugin.getAPI().getEventBus().post(new PlotEnterEvent(current, wrappedPlayer));

                PlotMapInfo pmi = manager.getMap(location.getWorld());
                if (pmi != null && pmi.hasPlotEnterAnnouncement()) {
                    String owner = current.getOwner();
                    if (owner == null || owner.isEmpty()) {
                        owner = "?";
                    }
                    wrappedPlayer.sendMessage(plugin.getAPI().C("MsgEnteredPlot", current.getId(), owner));
                }
            }
        }

        lastPlot.put(playerId, currentId);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerJoin(PlayerJoinEvent event) {
        IPlayer player = plugin.wrapPlayer(event.getPlayer());

        // Seed the last-plot tracker so the very next move doesn't fire a
        // spurious enter event from a stale (null) baseline.
        if (manager.isPlotWorld(player)) {
            PlotId currentId = manager.getPlotId(player);
            lastPlot.put(event.getPlayer().getUniqueId(), currentId);
        }

        if (manager.isPlotWorld(player) && !player.hasPermission(PermissionNames.ADMIN_BYPASSDENY)) {
            Plot plot = manager.getPlot(player);
            if (plot != null) {
                if (plot.getOwnerId().equals(event.getPlayer().getUniqueId())) {
                    return;
                }
                if (plot.isDenied(player.getUniqueId())) {
                    player.setLocation(manager.getPlotHome(plot.getId(), player.getWorld()));
                }
            }
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        lastPlot.remove(event.getPlayer().getUniqueId());
    }
}
