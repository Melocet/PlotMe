package com.worldcretornica.plotme_core.bukkit.integration;

import com.worldcretornica.plotme_core.Plot;
import com.worldcretornica.plotme_core.PlotMe_Core;
import org.bukkit.Bukkit;

import java.util.logging.Logger;

/**
 * Thin facade for the squaremap marker hook. References only Bukkit/JDK
 * types so the JVM can load this class even when squaremap isn't installed.
 *
 * <p>All squaremap-API contact lives in {@link SquaremapImpl}, which is only
 * instantiated when the squaremap plugin is enabled. Because the JVM defers
 * linking of {@code SquaremapImpl} until the {@code new SquaremapImpl(...)}
 * bytecode actually executes, the squaremap types are never resolved on
 * servers without squaremap.
 */
public class SquaremapHook implements WebMapHook {

    private final boolean available;
    private final SquaremapImpl impl;

    public SquaremapHook(PlotMe_Core plugin, Logger logger, String hexColor) {
        this.available = Bukkit.getPluginManager().isPluginEnabled("squaremap");
        if (available) {
            this.impl = new SquaremapImpl(plugin, logger, hexColor);
            logger.info("[PlotMe] squaremap integration enabled.");
        } else {
            this.impl = null;
        }
    }

    @Override public String getName() { return "squaremap"; }

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
}
