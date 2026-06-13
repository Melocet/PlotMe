package com.worldcretornica.plotme_core.api.event;

import com.worldcretornica.plotme_core.Plot;
import com.worldcretornica.plotme_core.api.IPlayer;

/**
 * Fired when a player enters a claimed plot. The plot is the plot that the
 * player has just entered.
 */
public class PlotEnterEvent extends PlotEvent implements Event {

    private final IPlayer player;

    public PlotEnterEvent(Plot plot, IPlayer player) {
        super(plot);
        this.player = player;
    }

    /**
     * Get the {@link IPlayer} that entered the plot.
     *
     * @return player that entered the plot
     */
    public IPlayer getPlayer() {
        return player;
    }
}
