package com.worldcretornica.plotme_core;

import com.worldcretornica.plotme_core.api.IPlotMe_GeneratorManager;
import com.worldcretornica.plotme_core.utils.ChunkEntry;
import com.worldcretornica.plotme_core.utils.ClearEntry;

import java.util.ArrayDeque;

/**
 * Drives plot clears in small, bounded slices each tick so a big plot
 * doesn't freeze the main thread. The work is chunk-by-chunk:
 * {@link com.worldcretornica.plotme_core.api.IPlotMe_GeneratorManager#clear}
 * populates {@link ClearEntry#chunkqueue} once with one {@link ChunkEntry}
 * per chunk overlapping the plot; this runnable then drains that queue.
 *
 * Tick budget:
 *  - We pop entries until we have either set ~{@link #MAX_BLOCKS_PER_TICK}
 *    blocks or spent ~{@link #MAX_NANOS_PER_TICK} nanoseconds in this tick.
 *  - We always run at least one entry per tick so single-entry queues
 *    still drain even if their estimate is huge.
 *  - Progression stays deterministic and pause-safe: when the budget
 *    runs out we just return and the next tick picks up where we left
 *    off — no per-tick reordering, no partial chunk state.
 */
public class PlotMeSpool implements Runnable {

    public static ArrayDeque<ClearEntry> clearList = new ArrayDeque<>();

    /**
     * Soft budget per tick. Once we've set this many blocks (estimated)
     * we stop draining for this tick. Tuned so a typical 32×32 plot
     * (~4 chunks, ~1.5M blocks across the full Y range) finishes inside
     * ~30 ticks at the 2-tick spool interval.
     */
    private static final int MAX_BLOCKS_PER_TICK = 4096 * 16;

    /** Hard time cap — if a chunk turns out to be expensive we bail early. */
    private static final long MAX_NANOS_PER_TICK = 25_000_000L; // 25 ms

    private final PlotMe_Core plugin;

    public PlotMeSpool(PlotMe_Core plotMe_core) {
        this.plugin = plotMe_core;
    }

    @Override
    public void run() {
        if (clearList.isEmpty()) {
            return;
        }

        final long deadline = System.nanoTime() + MAX_NANOS_PER_TICK;
        int blocksThisTick = 0;
        boolean ranAtLeastOne = false;

        while (!clearList.isEmpty()) {
            ClearEntry first = clearList.getFirst();

            // Populate the chunk queue on the first tick we see this
            // entry. Doing this every tick (as the old code did) would
            // re-enqueue every chunk forever, so the queue grew faster
            // than it drained.
            if (!first.populated) {
                IPlotMe_GeneratorManager genmanager =
                        PlotMeCoreManager.getInstance().getGenManager(first.getPlot().getWorld());
                genmanager.clear(
                        first.getPlot().getPlotBottomLoc(),
                        first.getPlot().getPlotTopLoc(),
                        first.getPlot().getId(),
                        first);
                first.populated = true;
            }

            if (first.chunkqueue.isEmpty()) {
                // This plot is done — finalize it and move on.
                IPlotMe_GeneratorManager genmanager =
                        PlotMeCoreManager.getInstance().getGenManager(first.getPlot().getWorld());
                if (first.getReason().equals(ClearReason.Clear)) {
                    genmanager.adjustPlotFor(first.getPlot(), true, false, false);
                } else {
                    genmanager.adjustPlotFor(first.getPlot(), false, false, false);
                }
                clearList.removeFirst();
                if (first.getSender() != null) {
                    first.getSender().sendMessage(
                            plugin.C("WordPlot") + " "
                            + first.getPlot().getId().getID() + " "
                            + plugin.C("WordCleared"));
                }
                // Continue the loop — there may be another queued plot
                // and we may still have budget left.
                continue;
            }

            // Run one chunk. We always run at least one per tick so
            // we make forward progress even when the estimate exceeds
            // the budget.
            if (ranAtLeastOne && (blocksThisTick >= MAX_BLOCKS_PER_TICK
                    || System.nanoTime() >= deadline)) {
                return;
            }

            ChunkEntry next = first.chunkqueue.poll();
            next.run();
            blocksThisTick += next.getEstimatedBlocks();
            ranAtLeastOne = true;

            if (blocksThisTick >= MAX_BLOCKS_PER_TICK
                    || System.nanoTime() >= deadline) {
                return;
            }
        }
    }
}
