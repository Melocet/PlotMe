package com.worldcretornica.plotme_core.bukkit.integration;

import com.worldcretornica.plotme_core.Plot;
import com.worldcretornica.plotme_core.PlotMe_Core;
import com.worldcretornica.plotme_core.api.IWorld;
import com.worldcretornica.plotme_core.api.Vector;
import com.worldcretornica.plotme_core.bukkit.api.BukkitWorld;
import de.bluecolored.bluemap.api.BlueMapAPI;
import de.bluecolored.bluemap.api.BlueMapMap;
import de.bluecolored.bluemap.api.BlueMapWorld;
import de.bluecolored.bluemap.api.markers.ExtrudeMarker;
import de.bluecolored.bluemap.api.markers.MarkerSet;
import de.bluecolored.bluemap.api.math.Color;
import de.bluecolored.bluemap.api.math.Shape;

import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Deferred implementation of the BlueMap marker hook. This class is only
 * referenced from {@link BlueMapHook}'s "available" branch, so the JVM only
 * loads it (and resolves its BlueMap-API imports) when the BlueMap plugin
 * is present on the server.
 */
class BlueMapImpl {

    private static final String MARKER_SET_ID = "plotme.plots";
    private static final String MARKER_SET_LABEL = "PlotMe Plots";

    private final PlotMe_Core plugin;
    private final Logger logger;
    private final int rgb;
    private final Color markerColor;

    BlueMapImpl(PlotMe_Core plugin, Logger logger, int rgbColor) {
        this.plugin = plugin;
        this.logger = logger;
        this.rgb = rgbColor;
        this.markerColor = new Color(rgb, 0.4f);
    }

    private Color color() {
        return markerColor;
    }

    void addMarker(Plot plot) {
        if (plot == null) return;
        try {
            Optional<BlueMapAPI> apiOpt = BlueMapAPI.getInstance();
            if (!apiOpt.isPresent()) return; // BlueMap not yet finished loading
            BlueMapAPI api = apiOpt.get();

            IWorld world = plot.getWorld();
            if (!(world instanceof BukkitWorld)) return;
            Optional<BlueMapWorld> bmWorldOpt = api.getWorld(((BukkitWorld) world).getWorld());
            if (!bmWorldOpt.isPresent()) return;

            Vector bottom = plot.getPlotBottomLoc();
            Vector top = plot.getPlotTopLoc();
            Shape shape = Shape.createRect(
                    bottom.getBlockX(), bottom.getBlockZ(),
                    top.getBlockX() + 1, top.getBlockZ() + 1);

            String markerId = markerId(plot);
            String label = plot.getOwner() == null ? "Unclaimed" : plot.getOwner();

            for (BlueMapMap map : bmWorldOpt.get().getMaps()) {
                MarkerSet set = map.getMarkerSets()
                        .computeIfAbsent(MARKER_SET_ID, k -> MarkerSet.builder()
                                .label(MARKER_SET_LABEL)
                                .toggleable(true)
                                .defaultHidden(false)
                                .build());

                ExtrudeMarker marker = ExtrudeMarker.builder()
                        .label(label)
                        .shape(shape, 63f, 80f)
                        .fillColor(color())
                        .lineColor(color())
                        .lineWidth(2)
                        .depthTestEnabled(false)
                        .build();

                set.getMarkers().put(markerId, marker);
            }
        } catch (Throwable t) {
            logger.log(Level.WARNING, "[PlotMe] BlueMap addMarker failed for " + safeId(plot), t);
        }
    }

    void removeMarker(Plot plot) {
        if (plot == null) return;
        try {
            Optional<BlueMapAPI> apiOpt = BlueMapAPI.getInstance();
            if (!apiOpt.isPresent()) return;
            IWorld world = plot.getWorld();
            if (!(world instanceof BukkitWorld)) return;
            Optional<BlueMapWorld> bmWorldOpt = apiOpt.get().getWorld(((BukkitWorld) world).getWorld());
            if (!bmWorldOpt.isPresent()) return;

            String markerId = markerId(plot);
            for (BlueMapMap map : bmWorldOpt.get().getMaps()) {
                MarkerSet set = map.getMarkerSets().get(MARKER_SET_ID);
                if (set != null) set.getMarkers().remove(markerId);
            }
        } catch (Throwable t) {
            logger.log(Level.WARNING, "[PlotMe] BlueMap removeMarker failed for " + safeId(plot), t);
        }
    }

    void refreshAll() {
        try {
            // Clear existing marker set on every map first, then repopulate.
            BlueMapAPI.getInstance().ifPresent(api -> {
                for (BlueMapWorld w : api.getWorlds()) {
                    for (BlueMapMap m : w.getMaps()) {
                        MarkerSet set = m.getMarkerSets().get(MARKER_SET_ID);
                        if (set != null) set.getMarkers().clear();
                    }
                }
            });
            for (Plot plot : plugin.getSqlManager().getPlots()) {
                addMarker(plot);
            }
        } catch (Throwable t) {
            logger.log(Level.WARNING, "[PlotMe] BlueMap refreshAll failed", t);
        }
    }

    private static String markerId(Plot plot) {
        return "plotme." + plot.getWorld().getName() + "." + plot.getId().getID();
    }

    private static String safeId(Plot plot) {
        try { return markerId(plot); } catch (Throwable ignored) { return "<unknown>"; }
    }
}
