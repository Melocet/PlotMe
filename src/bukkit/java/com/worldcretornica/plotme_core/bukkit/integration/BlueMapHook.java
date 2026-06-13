package com.worldcretornica.plotme_core.bukkit.integration;

import com.worldcretornica.plotme_core.Plot;
import com.worldcretornica.plotme_core.PlotMe_Core;
import org.bukkit.Bukkit;

import java.util.logging.Logger;

/**
 * Thin facade for the BlueMap marker hook. References no BlueMap-API types
 * directly — all real work lives in {@link BlueMapImpl}, which is only
 * referenced (and thus only loaded by the JVM) when BlueMap is actually
 * enabled. This keeps PlotMe loadable on servers that don't ship BlueMap.
 */
public class BlueMapHook implements WebMapHook {

    private final boolean available;
    private final BlueMapImpl impl;

    public BlueMapHook(PlotMe_Core plugin, Logger logger, String hexColor) {
        this.available = Bukkit.getPluginManager().isPluginEnabled("BlueMap");
        if (available) {
            this.impl = new BlueMapImpl(plugin, logger, parseRgb(hexColor));
            logger.info("[PlotMe] BlueMap integration enabled.");
        } else {
            this.impl = null;
        }
    }

    @Override public String getName() { return "BlueMap"; }

    @Override public boolean isAvailable() { return available; }

    @Override
    public void addMarker(Plot plot) {
        if (!available || impl == null) return;
        impl.addMarker(plot);
    }

    @Override
    public void removeMarker(Plot plot) {
        if (!available || impl == null) return;
        impl.removeMarker(plot);
    }

    @Override
    public void refreshAll() {
        if (!available || impl == null) return;
        impl.refreshAll();
    }

    /**
     * Parses the configured hex into a plain RGB int. Returns the default
     * orange (0xFFA500) on any parse failure. Pure primitive — does NOT
     * touch any BlueMap-API class, so this stays safe to call when BlueMap
     * is missing from the server.
     */
    private static int parseRgb(String hex) {
        try {
            if (hex == null) return 0xFFA500;
            String h = hex.trim();
            if (h.startsWith("#")) h = h.substring(1);
            return Integer.parseInt(h, 16);
        } catch (Throwable ignored) {
            return 0xFFA500;
        }
    }
}
