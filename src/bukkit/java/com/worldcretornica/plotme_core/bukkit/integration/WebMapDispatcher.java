package com.worldcretornica.plotme_core.bukkit.integration;

import com.worldcretornica.plotme_core.Plot;
import com.worldcretornica.plotme_core.PlotMe_Core;
import com.worldcretornica.plotme_core.api.event.PlotDisposeEvent;
import com.worldcretornica.plotme_core.api.event.PlotLoadEvent;
import com.worldcretornica.plotme_core.api.event.eventbus.Order;
import com.worldcretornica.plotme_core.api.event.eventbus.Subscribe;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * Glue between PlotMe's EventBus and the {@link WebMapHook} implementations.
 *
 * <p>Reads the {@code webmap:} config section, instantiates each enabled hook,
 * and fans out PlotLoad / PlotDispose events to every available hook. Hooks
 * that aren't available are still wired (their methods no-op) so /plotme
 * reload after a plugin install becomes a soft re-enable.
 */
public class WebMapDispatcher {

    private final List<WebMapHook> hooks = new ArrayList<>();

    public WebMapDispatcher(PlotMe_Core plugin, Logger logger) {
        ConfigurationSection cfg = plugin.getConfig().getConfigurationSection("webmap");
        boolean bluemap = cfg == null || cfg.getBoolean("bluemap", true);
        boolean squaremap = cfg == null || cfg.getBoolean("squaremap", true);
        String color = cfg == null ? "#FFA500" : cfg.getString("marker-color", "#FFA500");

        if (bluemap) hooks.add(new BlueMapHook(plugin, logger, color));
        if (squaremap) hooks.add(new SquaremapHook(plugin, logger, color));
    }

    /** Whether any hook is actually available (backing plugin loaded). */
    public boolean anyAvailable() {
        for (WebMapHook h : hooks) if (h.isAvailable()) return true;
        return false;
    }

    /** Rebuild every marker from the database. Call on /plotme reload. */
    public void refreshAll() {
        for (WebMapHook h : hooks) h.refreshAll();
    }

    @Subscribe(order = Order.LATE)
    public void onPlotLoad(PlotLoadEvent event) {
        Plot plot = event.getPlot();
        for (WebMapHook h : hooks) h.addMarker(plot);
    }

    @Subscribe(order = Order.LATE)
    public void onPlotDispose(PlotDisposeEvent event) {
        Plot plot = event.getPlot();
        for (WebMapHook h : hooks) h.removeMarker(plot);
    }
}
