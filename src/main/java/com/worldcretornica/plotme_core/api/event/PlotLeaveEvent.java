package com.worldcretornica.plotme_core.api.event;

import com.worldcretornica.plotme_core.Plot;
import com.worldcretornica.plotme_core.api.IPlayer;

/**
 * Fired when a player leaves a claimed plot. The plot is the plot that the
 * player has just left.
 */
public class PlotLeaveEvent extends PlotEvent implements Event {

    private final IPlayer player;

    public PlotLeaveEvent(Plot plot, IPlayer player) {
        super(plot);
        this.player = player;
    }

    /**
     * Get the {@link IPlayer} that left the plot.
     *
     * @return player that left the plot
     */
    public IPlayer getPlayer() {
        return player;
    }
}
