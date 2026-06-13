package com.worldcretornica.plotme_core.bukkit.integration;

import com.worldcretornica.plotme_core.Plot;

/**
 * Common contract for a web-map marker provider (BlueMap, squaremap, etc).
 *
 * <p>Implementations are softdeps — they check {@code isPluginEnabled(...)} at
 * construction. If the backing plugin is missing, {@link #isAvailable()}
 * returns {@code false} and all methods are no-ops, so callers don't need to
 * branch.
 *
 * <p>All calls are wrapped in try/catch inside the implementations so that a
 * broken hook (e.g. an API mismatch after a plugin update) never propagates an
 * exception into PlotMe's event pipeline.
 */
public interface WebMapHook {

    /** @return short name of the backing plugin, for log lines. */
    String getName();

    /** @return true if the backing plugin is loaded and the hook is ready. */
    boolean isAvailable();

    /** Add or update the marker for a single plot. */
    void addMarker(Plot plot);

    /** Remove the marker for a single plot. */
    void removeMarker(Plot plot);

    /** Wipe and re-add markers for every loaded plot — used on /plotme reload. */
    void refreshAll();
}
