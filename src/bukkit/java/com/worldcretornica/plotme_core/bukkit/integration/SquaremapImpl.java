package com.worldcretornica.plotme_core.bukkit.integration;

import com.worldcretornica.plotme_core.Plot;
import com.worldcretornica.plotme_core.PlotMe_Core;
import com.worldcretornica.plotme_core.api.IWorld;
import com.worldcretornica.plotme_core.api.Vector;
import com.worldcretornica.plotme_core.bukkit.api.BukkitWorld;
import xyz.jpenilla.squaremap.api.Key;
import xyz.jpenilla.squaremap.api.MapWorld;
import xyz.jpenilla.squaremap.api.Point;
import xyz.jpenilla.squaremap.api.SimpleLayerProvider;
import xyz.jpenilla.squaremap.api.Squaremap;
import xyz.jpenilla.squaremap.api.SquaremapProvider;
import xyz.jpenilla.squaremap.api.WorldIdentifier;
import xyz.jpenilla.squaremap.api.marker.Marker;
import xyz.jpenilla.squaremap.api.marker.MarkerOptions;
import xyz.jpenilla.squaremap.api.marker.Rectangle;

import java.awt.Color;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Deferred implementation of the squaremap marker hook. Loaded only when
 * squaremap is present on the server — the {@link SquaremapHook} facade
 * gates {@code new SquaremapImpl(...)} behind a plugin-enabled check so the
 * JVM never resolves the squaremap API types when the plugin is absent.
 */
final class SquaremapImpl {

    static final String LAYER_KEY_NAME = "plotme_plots";
    static final String LAYER_LABEL = "PlotMe Plots";

    private final PlotMe_Core plugin;
    private final Logger logger;
    private final Color markerColor;

    private static Key layerKey() { return Key.of(LAYER_KEY_NAME); }

    SquaremapImpl(PlotMe_Core plugin, Logger logger, String hexColor) {
        this.plugin = plugin;
        this.logger = logger;
        this.markerColor = parseColor(hexColor);
    }

    void addMarker(Plot plot) {
        if (plot == null) return;
        try {
            Optional<SimpleLayerProvider> layerOpt = layerFor(plot.getWorld());
            if (!layerOpt.isPresent()) return;

            Vector bottom = plot.getPlotBottomLoc();
            Vector top = plot.getPlotTopLoc();
            Rectangle rect = Marker.rectangle(
                    Point.of(bottom.getBlockX(), bottom.getBlockZ()),
                    Point.of(top.getBlockX() + 1, top.getBlockZ() + 1));

            String label = plot.getOwner() == null ? "Unclaimed" : plot.getOwner();
            rect.markerOptions(MarkerOptions.builder()
                    .strokeColor(markerColor)
                    .strokeWeight(2)
                    .fillColor(withAlpha(markerColor, 102))
                    .hoverTooltip(label)
                    .clickTooltip(label)
                    .build());

            layerOpt.get().addMarker(Key.of(markerId(plot)), rect);
        } catch (Throwable t) {
            logger.log(Level.WARNING, "[PlotMe] squaremap addMarker failed for " + safeId(plot), t);
        }
    }

    void removeMarker(Plot plot) {
        if (plot == null) return;
        try {
            Optional<SimpleLayerProvider> layerOpt = layerFor(plot.getWorld());
            if (!layerOpt.isPresent()) return;
            layerOpt.get().removeMarker(Key.of(markerId(plot)));
        } catch (Throwable t) {
            logger.log(Level.WARNING, "[PlotMe] squaremap removeMarker failed for " + safeId(plot), t);
        }
    }

    void refreshAll() {
        try {
            // Drop every PlotMe layer first, then rebuild from the DB snapshot.
            Squaremap api = SquaremapProvider.get();
            for (MapWorld mw : api.mapWorlds()) {
                mw.layerRegistry().unregister(layerKey());
            }
            for (Plot plot : plugin.getSqlManager().getPlots()) {
                addMarker(plot);
            }
        } catch (Throwable t) {
            logger.log(Level.WARNING, "[PlotMe] squaremap refreshAll failed", t);
        }
    }

    private Optional<SimpleLayerProvider> layerFor(IWorld world) {
        if (!(world instanceof BukkitWorld)) return Optional.empty();
        org.bukkit.World bukkitWorld = ((BukkitWorld) world).getWorld();
        Squaremap api = SquaremapProvider.get();
        Optional<MapWorld> mw = api.getWorldIfEnabled(WorldIdentifier.create(
                bukkitWorld.getKey().getNamespace(), bukkitWorld.getKey().getKey()));
        if (!mw.isPresent()) return Optional.empty();
        SimpleLayerProvider layer = (SimpleLayerProvider)
                mw.get().layerRegistry().get(layerKey());
        if (layer == null) {
            layer = SimpleLayerProvider.builder(LAYER_LABEL)
                    .showControls(true)
                    .defaultHidden(false)
                    .build();
            mw.get().layerRegistry().register(layerKey(), layer);
        }
        return Optional.of(layer);
    }

    private static String markerId(Plot plot) {
        return "plotme_" + plot.getWorld().getName() + "_" + plot.getId().getID();
    }

    private static String safeId(Plot plot) {
        try { return markerId(plot); } catch (Throwable ignored) { return "<unknown>"; }
    }

    private static Color withAlpha(Color c, int alpha) {
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), alpha);
    }

    private Color parseColor(String hex) {
        try {
            if (hex == null) return new Color(0xFF, 0xA5, 0x00);
            String h = hex.trim();
            if (h.startsWith("#")) h = h.substring(1);
            int rgb = Integer.parseInt(h, 16);
            return new Color(rgb);
        } catch (Throwable ignored) {
            return new Color(0xFF, 0xA5, 0x00);
        }
    }
}
