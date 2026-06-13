package com.worldcretornica.plotme_core.api;

import com.worldcretornica.plotme_core.Plot;
import com.worldcretornica.plotme_core.PlotId;
import com.worldcretornica.plotme_core.utils.ClearEntry;

import java.util.List;

public interface IPlotMe_GeneratorManager {

    PlotId getPlotId(Vector loc);

    PlotId getPlotId(IPlayer player);

    List<IPlayer> getPlayersInPlot(PlotId id);

    void clearEntities(Vector bottom, Vector top);

    void fillRoad(PlotId id1, PlotId id2);

    void fillMiddleRoad(PlotId id1, PlotId id2);

    /**
     * Clear the central path-width x path-width intersection where the four
     * roads meet between the 2x2 cluster {@code nw / ne / sw / se}. Without
     * this, a 2x2 merge done as four edge-to-edge pairs (which is how
     * /plotme merge composes) leaves the center square as untouched road —
     * unclaimed-wall slabs around a sand/dirt floor. Default implementation
     * is a no-op so non-default generators that don't carry the concept stay
     * source-compatible.
     */
    default void fillCenterIntersection(PlotId nw, PlotId ne, PlotId sw, PlotId se) {
        // no-op fallback
    }

    /**
     * Inverse of {@link #fillRoad(PlotId, PlotId)} — rebuilds the original
     * road strip between two adjacent plots that USED to be merged. Restores
     * the unclaimed-wall slabs along the outer edges (and along the boundary
     * facing each plot, so each plot regains its own perimeter wall), the
     * road floor block at the ground level, and the regular fill column
     * below. Air above the wall layer.
     *
     * Called by the dispose flow when one member of a merged cluster is
     * disposed: every road strip that fillRoad had carved between the
     * disposed plot and its merged neighbours needs to be put back. Default
     * implementation is a no-op so non-default generators stay source-compatible.
     */
    default void rebuildRoad(PlotId id1, PlotId id2) {
        // no-op fallback
    }

    /**
     * Inverse of {@link #fillCenterIntersection(PlotId, PlotId, PlotId, PlotId)}
     * — restores the path-width x path-width centre square between a 2x2
     * cluster to its original road pattern (unclaimed-wall slabs around the
     * four flanking road strips' corners, road floor at ground level). Called
     * by the dispose flow when removing a plot that was the 2x2 connector of
     * its cluster. Default implementation is a no-op.
     */
    default void rebuildCenterIntersection(PlotId nw, PlotId ne, PlotId sw, PlotId se) {
        // no-op fallback
    }

    void setOwnerDisplay(PlotId id, String line1, String line2, String line3, String line4);

    void setSellerDisplay(PlotId id, String line1, String line2, String line3, String line4);

    void removeOwnerDisplay(PlotId id);

    void removeSellerDisplay(PlotId id);

    Vector getPlotBottomLoc(PlotId id);

    Vector getPlotTopLoc(PlotId id);

    void refreshPlotChunks(PlotId id);

    Vector getTop(PlotId id);

    Vector getBottom(PlotId id);

    void clear(Vector bottom, Vector top, PlotId clearMap, ClearEntry entry);

    void adjustPlotFor(Plot id, boolean claimed, boolean protect, boolean forSale);

    boolean isBlockInPlot(PlotId id, Vector location);

    boolean movePlot(PlotId idFrom, PlotId idTo);

    int bottomX(PlotId id);

    int bottomZ(PlotId id);

    int topX(PlotId id);

    int topZ(PlotId id);

    Location getPlotHome(PlotId id);

    int getPlotSize();

    int getGroundHeight();

    Vector getPlotMiddle(PlotId id);

    /**
     * Paint {@code biome} across every XZ column owned by the plot {@code id}
     * (rectangular range from {@link #getPlotBottomLoc} to {@link #getPlotTopLoc},
     * inclusive on both ends). Returns the number of block columns touched so
     * callers can report a meaningful "N blocks" count to the player. A return
     * of 0 means the biome key was unknown to the registry — the manager makes
     * no changes in that case.
     */
    int setBiome(PlotId id, String biome);

    /**
     * Paint a named biome over an arbitrary XZ rectangle (useful for merged
     * road strips and 4-way intersections that aren't part of any single
     * plot's bounding box). Returns the block-column count touched.
     * Default no-op for third-party generators that don't support it.
     */
    default int setBiomeRegion(IWorld world, int minX, int maxX, int minZ, int maxZ, String biome) {
        return 0;
    }
}
