package com.worldcretornica.plotme_core.utils;

import com.worldcretornica.plotme_core.ClearReason;
import com.worldcretornica.plotme_core.Plot;
import com.worldcretornica.plotme_core.api.ICommandSender;

import java.util.ArrayDeque;

public class ClearEntry {

    private final Plot plot;
    private final ClearReason reason;
    private final ICommandSender sender;
    public ArrayDeque<ChunkEntry> chunkqueue = new ArrayDeque<>();
    /**
     * Flag flipped by {@link com.worldcretornica.plotme_core.PlotMeSpool}
     * after the first tick that calls {@code IPlotMe_GeneratorManager#clear}
     * to populate {@link #chunkqueue}. Without this guard the spool would
     * re-enqueue every chunk on every tick, so the queue would grow
     * faster than it drains and the clear would never complete.
     */
    public boolean populated = false;

    public ClearEntry(Plot plot, ClearReason reason, ICommandSender sender) {

        this.plot = plot;
        this.reason = reason;
        this.sender = sender;
    }

    public Plot getPlot() {
        return plot;
    }

    public ClearReason getReason() {
        return reason;
    }

    public ICommandSender getSender() {
        return sender;
    }
}
