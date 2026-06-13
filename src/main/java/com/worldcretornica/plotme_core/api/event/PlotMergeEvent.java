package com.worldcretornica.plotme_core.api.event;

import com.worldcretornica.plotme_core.Plot;
import com.worldcretornica.plotme_core.api.IPlayer;

/**
 * Fired right before two adjacent plots are merged together (either via
 * {@code /plotme merge <dir>} or via the auto-link path inside
 * {@link com.worldcretornica.plotme_core.PlotMeCoreManager#adjustLinkedPlots}).
 *
 * Cancelling this event aborts the merge, refunds any economy cost the
 * command consumed, and leaves the two plots independent.
 *
 * NOTE: this only fires for an interactive merge. Cross-plot building /
 * clearing semantics are not yet wired up — handlers that care about the
 * resulting cluster should treat the link as informational for now.
 */
public class PlotMergeEvent extends PlotEvent implements ICancellable, Event {

    private final Plot otherPlot;
    private final IPlayer player;
    private boolean canceled;

    public PlotMergeEvent(Plot fromPlot, Plot otherPlot, IPlayer player) {
        super(fromPlot);
        this.otherPlot = otherPlot;
        this.player = player;
    }

    @Override
    public boolean isCancelled() {
        return canceled;
    }

    @Override
    public void setCanceled(boolean cancel) {
        canceled = cancel;
    }

    /** The plot the command was issued from / the centre plot for auto-link. */
    public Plot getFromPlot() {
        return getPlot();
    }

    /** The neighbour the centre plot is being merged with. */
    public Plot getOtherPlot() {
        return otherPlot;
    }

    /** May be null when the merge originated from a system path. */
    public IPlayer getPlayer() {
        return player;
    }
}
