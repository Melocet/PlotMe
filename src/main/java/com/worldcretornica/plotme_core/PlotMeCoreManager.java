package com.worldcretornica.plotme_core;

import com.worldcretornica.plotme_core.api.ICommandSender;
import com.worldcretornica.plotme_core.api.IEntity;
import com.worldcretornica.plotme_core.api.IOfflinePlayer;
import com.worldcretornica.plotme_core.api.IPlayer;
import com.worldcretornica.plotme_core.api.IPlotMe_GeneratorManager;
import com.worldcretornica.plotme_core.api.IWorld;
import com.worldcretornica.plotme_core.api.Location;
import com.worldcretornica.plotme_core.api.Vector;
import com.worldcretornica.plotme_core.api.event.PlotLoadEvent;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import javax.inject.Singleton;

@Singleton
public class PlotMeCoreManager {

    private static final PlotMeCoreManager INSTANCE = new PlotMeCoreManager();
    private final HashMap<IWorld, PlotMapInfo> plotmaps = new HashMap<>();
    private final HashSet<UUID> playersignoringwelimit = new HashSet<>();
    private PlotMe_Core plugin;

    private PlotMeCoreManager() {
    }

    /**
     * This is the hook into retrieving the {@link #PlotMeCoreManager()}
     * @return instance of {@link #PlotMeCoreManager()}
     */
    public static PlotMeCoreManager getInstance() {
        return INSTANCE;
    }

    void setPlugin(PlotMe_Core instance) {
        plugin = instance;
    }

    /**
     * Removes the plot from the plotmap
     * @param plot plot id
     */
    public boolean deletePlot(Plot plot) {
        removeSellSign(plot);
        removeOwnerSign(plot);
        return plugin.getSqlManager().deletePlot(plot);
    }

    /**
     * Pre-dispose hook for a plot that may be part of a merged cluster.
     *
     * For an UN-merged plot this is a no-op — the caller will then run the
     * usual {@link #deletePlot(Plot)} and the geometry stays untouched.
     *
     * For a member of a merged cluster, this:
     *   1. Snapshots the disposed plot's mergedWith set BEFORE we mutate it.
     *   2. For every same-cluster 2x2 the disposed plot is a corner of and
     *      whose other three corners were ALSO in the pre-dispose cluster,
     *      calls {@code rebuildCenterIntersection} to restore the path-width
     *      x path-width centre square. We rebuild centres FIRST, before
     *      tearing down the perimeter strips, so the centre's neighbouring
     *      plot-edge walls get re-laid by the strip rebuild in step 3.
     *   3. For every direct mergedWith neighbour, calls {@code rebuildRoad}
     *      to put back the original unclaimed road strip and removes the
     *      bidirectional link from both Plot objects' mergedWith sets.
     *      Marks each neighbour dirty so the merged-rows table is rewritten
     *      on the next async flush — this is what makes the dispose survive
     *      a /restart.
     *   4. Repaints the perimeter wall of every remaining cluster member
     *      ({@code adjustWall(p, true)}) so the rows that were previously
     *      skipped (internal-edge merge skip) become claimed walls now that
     *      the boundary is on the outside of the cluster again.
     *   5. Refreshes the cluster's owner sign. The remaining members may
     *      have split into MULTIPLE disconnected components (the disposed
     *      plot was a bridge); we run a flood-fill over each remaining
     *      neighbour and refresh once per discovered component, so each
     *      component's outermost-NW gets its own sign.
     *
     * Caller is expected to invoke {@link #deletePlot(Plot)} AFTER this
     * method — the disposed plot's own row goes away in that step.
     *
     * @param disposed the plot being disposed; must not be null
     */
    public void disposeMergedPlot(Plot disposed) {
        if (disposed == null) {
            return;
        }
        IWorld world = disposed.getWorld();
        if (world == null) {
            return;
        }
        IPlotMe_GeneratorManager genMan = getGenManager(world);
        if (genMan == null) {
            return;
        }
        // Snapshot the disposed plot's neighbours before we mutate anything.
        // mergedWith is a live set, so we copy it.
        java.util.Set<PlotId> neighbourIds = new java.util.LinkedHashSet<>(disposed.getMergedWith());
        if (neighbourIds.isEmpty()) {
            return; // un-merged plot — no road rebuild needed
        }
        // Resolve neighbour Plot objects up-front (some may be null if the
        // SQL cache lost them; we filter those out for the rebuild loops but
        // still need to clear our own mergedWith reference to them).
        java.util.Map<PlotId, Plot> neighbours = new java.util.LinkedHashMap<>();
        for (PlotId nid : neighbourIds) {
            Plot p = getPlotById(nid, world);
            if (p != null) {
                neighbours.put(nid, p);
            }
        }

        // --- Step 1: 2x2 centre intersections involving the disposed plot.
        // The disposed plot can be any of NW/NE/SW/SE in up to four distinct
        // 2x2 squares. For each square, all four corners must have been in
        // the cluster BEFORE dispose (i.e., the disposed plot is mergedWith
        // each of the other three) so that fillCenterIntersection had been
        // called at merge time; otherwise there is no centre to restore.
        PlotId did = disposed.getId();
        int dx = did.x();
        int dz = did.z();
        // (nwOffsetX, nwOffsetZ) is the offset from the disposed plot to the
        // NW corner of the 2x2 square. (0,0) -> disposed IS the NW corner;
        // (-1,0) -> disposed is NE; (0,-1) -> SW; (-1,-1) -> SE.
        int[][] offsets = { {0, 0}, {-1, 0}, {0, -1}, {-1, -1} };
        for (int[] off : offsets) {
            PlotId nwId = new PlotId(dx + off[0],     dz + off[1]);
            PlotId neId = new PlotId(dx + off[0] + 1, dz + off[1]);
            PlotId swId = new PlotId(dx + off[0],     dz + off[1] + 1);
            PlotId seId = new PlotId(dx + off[0] + 1, dz + off[1] + 1);
            if (!isCornerOfPreDisposeCluster(disposed, nwId, neId, swId, seId, world)) {
                continue;
            }
            genMan.rebuildCenterIntersection(nwId, neId, swId, seId);
        }

        // --- Step 2: rebuild every direct-neighbour road strip + unlink.
        // mergedWith may contain DIAGONAL links too (mergePlots installs
        // them in its 2x2 sweep so getMergedCluster sees one connected
        // component even when the player merged in an L-shape). Diagonals
        // have no fillRoad-built strip between them — the geometry that
        // joined them was the centre intersection, which step 1 already
        // restored. So only rebuildRoad on the four cardinal neighbours
        // (Manhattan distance == 1); diagonals are unlink-only.
        for (java.util.Map.Entry<PlotId, Plot> entry : neighbours.entrySet()) {
            Plot neighbour = entry.getValue();
            PlotId nid = neighbour.getId();
            int manhattan = Math.abs(nid.x() - did.x()) + Math.abs(nid.z() - did.z());
            if (manhattan == 1) {
                genMan.rebuildRoad(disposed.getId(), nid);
            }
            // Unlink in both directions; the disposed plot's own row is
            // about to be deleted, but we still scrub its set so any
            // concurrent reader sees a consistent state.
            disposed.removeMergedWith(nid);
            neighbour.removeMergedWith(disposed.getId());
            // Persist the neighbour's updated merged-set on the next async
            // flush so /restart preserves the broken link.
            plugin.getSqlManager().markDirty(neighbour);
        }

        // --- Step 3: repaint the perimeter walls of every remaining
        // cluster member. The internal-edge skip flags inside adjustPlotFor
        // look at the live mergedWith set, which we just trimmed — so the
        // previously-skipped edges (where the disposed plot used to be the
        // merged partner) will now repaint as claimed walls.
        java.util.Set<Plot> repainted = new java.util.LinkedHashSet<>();
        for (Plot neighbour : neighbours.values()) {
            for (Plot member : getMergedCluster(neighbour)) {
                if (repainted.add(member)) {
                    adjustWall(member, true);
                }
            }
            // Also repaint the immediate neighbour itself in the un-merged
            // single-plot case (its cluster is just {neighbour}).
            if (repainted.add(neighbour)) {
                adjustWall(neighbour, true);
            }
        }

        // --- Step 4: refresh the owner sign once per remaining connected
        // component. The disposed plot may have been the only bridge
        // between two halves of the cluster, so a single refreshClusterOwnerSign
        // on one neighbour would miss the other component. We iterate
        // neighbours and flood-fill, refreshing each unique component once.
        java.util.Set<Plot> signRefreshed = new java.util.LinkedHashSet<>();
        for (Plot neighbour : neighbours.values()) {
            if (signRefreshed.contains(neighbour)) continue;
            java.util.Set<Plot> component = getMergedCluster(neighbour);
            signRefreshed.addAll(component);
            // Each component refreshes its own NW sign. If the component
            // shrank to a single plot (the bridge case), this falls through
            // to setSingleOwnerSign inside refreshClusterOwnerSign.
            refreshClusterOwnerSign(neighbour);
        }
    }

    /**
     * Helper for {@link #disposeMergedPlot(Plot)}: returns true if the four
     * corners {@code nwId/neId/swId/seId} of a 2x2 square were ALL part of
     * the pre-dispose cluster containing {@code disposed}. We test this by
     * checking that the three OTHER corners (i.e., the ones that aren't the
     * disposed plot itself) all still hold a mergedWith link to the disposed
     * plot — which is the condition that drives fillCenterIntersection at
     * merge time (see {@link #mergePlots(Plot, Plot)}).
     *
     * Returns false if any corner Plot is missing from the SQL cache or if
     * any non-disposed corner is not mergedWith the disposed plot.
     */
    private boolean isCornerOfPreDisposeCluster(Plot disposed, PlotId nwId, PlotId neId, PlotId swId, PlotId seId, IWorld world) {
        Plot pNW = nwId.equals(disposed.getId()) ? disposed : getPlotById(nwId, world);
        Plot pNE = neId.equals(disposed.getId()) ? disposed : getPlotById(neId, world);
        Plot pSW = swId.equals(disposed.getId()) ? disposed : getPlotById(swId, world);
        Plot pSE = seId.equals(disposed.getId()) ? disposed : getPlotById(seId, world);
        if (pNW == null || pNE == null || pSW == null || pSE == null) {
            return false;
        }
        // Every non-disposed corner must be linked to the disposed plot.
        // (mergePlots also linkMergedPlots(p, se) and (ne, sw) — so the
        // diagonal links are present too — but the direct edge-to-disposed
        // link is the strongest single check.)
        PlotId d = disposed.getId();
        if (pNW != disposed && !pNW.isMergedWith(d)) return false;
        if (pNE != disposed && !pNE.isMergedWith(d)) return false;
        if (pSW != disposed && !pSW.isMergedWith(d)) return false;
        if (pSE != disposed && !pSE.isMergedWith(d)) return false;
        return true;
    }

    /**
     * Sets the sign for the plot owner.
     *
     * For an un-merged plot this places a sign at the plot's own NW corner
     * with the plot id on line 1.
     *
     * For a member of a merged cluster, only the outermost NW member (lowest
     * x, then lowest z) keeps a sign — its line 4 holds the comma-joined
     * cluster ids ("1;1,1;2,2;1,2;2") so a viewer can see which plots are
     * fused together. Every other member of the cluster gets its own sign
     * removed: otherwise every plot's per-plot NW sign would land in the
     * interior of the merged area, which is what {@code DefaultPlotManager
     * .setOwnerDisplay} produces by default.
     *
     * @param plot  plot to set sign on
     */
    public void setOwnerSign(Plot plot) {
        if (plot.getMergedWith().isEmpty()) {
            setSingleOwnerSign(plot);
            return;
        }
        // Merged: refresh the whole cluster — outermost NW gets the sign,
        // everyone else gets theirs removed.
        refreshClusterOwnerSign(plot);
    }

    /**
     * Place an owner sign at a single plot's own NW corner. Used both for
     * un-merged plots and for the outermost-NW member of a merged cluster.
     * Lines: 1 = ID, 2 = blank, 3 = owner name, 4 = caller-supplied (cluster
     * ids for a merged cluster, blank for a single plot).
     */
    private void setSingleOwnerSign(Plot plot) {
        PlotId id = plot.getId();
        String line1 = "ID: " + id.toString();
        String line2 = "";
        String line3 = plot.getOwner();
        String line4 = "";
        getGenManager(plot.getWorld()).setOwnerDisplay(id, line1, line2, line3, line4);
    }

    /**
     * Refresh the owner sign for an entire merged cluster from any starting
     * member. Computes the cluster's outermost-NW PlotId (smallest x, then
     * smallest z), places the sign there with the joined cluster id list on
     * line 4, and removes the per-plot signs on every other member.
     *
     * Safe to call repeatedly; each call rewrites the sign block in place.
     */
    public void refreshClusterOwnerSign(Plot anyMember) {
        if (anyMember == null) {
            return;
        }
        java.util.Set<Plot> cluster = getMergedCluster(anyMember);
        if (cluster.isEmpty()) {
            return;
        }
        if (cluster.size() == 1) {
            // No real cluster — treat as single plot.
            setSingleOwnerSign(anyMember);
            return;
        }
        // Outermost-NW = min x, then min z. The other members get their
        // signs cleared.
        Plot outermost = null;
        for (Plot p : cluster) {
            if (outermost == null) {
                outermost = p;
                continue;
            }
            PlotId a = outermost.getId();
            PlotId b = p.getId();
            if (b.x() < a.x() || (b.x() == a.x() && b.z() < a.z())) {
                outermost = p;
            }
        }
        // Build a stable, sorted "x;z,x;z,..." list of every member of the
        // cluster (sorted by x then z) so the sign reads predictably and two
        // members of the same cluster produce the exact same text.
        java.util.List<PlotId> ids = new java.util.ArrayList<>();
        for (Plot p : cluster) ids.add(p.getId());
        ids.sort((a, b) -> {
            int cmp = Integer.compare(a.x(), b.x());
            return cmp != 0 ? cmp : Integer.compare(a.z(), b.z());
        });
        StringBuilder line4 = new StringBuilder();
        for (int i = 0; i < ids.size(); i++) {
            if (i > 0) line4.append(',');
            line4.append(ids.get(i).getID());
        }
        // Place the cluster sign on the outermost-NW member, clear the
        // others. Use the gen manager directly so we don't recurse through
        // setOwnerSign for the non-outermost members.
        IPlotMe_GeneratorManager gm = getGenManager(outermost.getWorld());
        String line1 = "ID: " + outermost.getId().toString();
        String line3 = outermost.getOwner();
        gm.setOwnerDisplay(outermost.getId(), line1, "", line3, line4.toString());
        for (Plot p : cluster) {
            if (p == outermost) continue;
            gm.removeOwnerDisplay(p.getId());
        }
    }

    /**
     * Get the id of the plot based on the location
     *
     * @param location location in the plotworld
     * @return Plot ID or an empty string if not found
     */
    public PlotId getPlotId(Location location) {
        if (getGenManager(location.getWorld()) == null) {
            return null;
        }
        return getGenManager(location.getWorld()).getPlotId(location.getVector());

    }

    /**
     * Get the id of the plot the player is standing on
     *
     * @param player player in the plotworld
     * @return Plot ID or an empty string if not found
     */
    public PlotId getPlotId(IPlayer player) {
        if (getGenManager(player.getWorld()) == null) {
            return null;
        }
        return getGenManager(player.getWorld()).getPlotId(player);

    }

    /**
     * Removes the owner sign from the plot.
     * @param plot    plot to remove the sign from
     */
    public void removeOwnerSign(Plot plot) {
        getGenManager(plot.getWorld()).removeOwnerDisplay(plot.getId());
    }

    /**
     * Remove the sell sign from the plot
     * @param plot    plot id to remove the sign from
     */
    public void removeSellSign(Plot plot) {
        getGenManager(plot.getWorld()).removeSellerDisplay(plot.getId());
    }

    /**
     * Set the sell sign on the plot
     *
     * @param plot  plot to add sign to
     */
    public void setSellSign(Plot plot) {
        String line1 = plugin.C("SignForSale");
        String line2 = plugin.C("SignPrice", plot.getPrice());
        String line4 = "/plotme buy";

        getGenManager(plot.getWorld()).setSellerDisplay(plot.getId(), line1, line2, "", line4);
    }

    /**
     * Gets the bottom corner of the plot.
     *
     * Defensively returns a (0,0,0) Vector when the world has no registered
     * generator manager yet (e.g. /mv create just ran but our WorldInitEvent
     * hook hasn't fired, or the world was created with a non-PlotMe
     * generator). Callers reaching this branch should treat it as "not a
     * plot world" — isPlotAvailable now short-circuits before calling us in
     * that state.
     *
     * @param world
     * @param id PlotID
     * @return bottom location of the plot, or a zero Vector when gen manager is missing
     */
    @Deprecated
    public Vector getPlotBottomLoc(IWorld world, PlotId id) {
        IPlotMe_GeneratorManager gm = getGenManager(world);
        if (gm == null) {
            return new Vector(0, 0, 0);
        }
        return gm.getPlotBottomLoc(id);
    }

    /**
     * Gets the top corner of the plot.
     *
     * Defensively returns a (0,0,0) Vector when no gen manager is registered
     * for the world. See getPlotBottomLoc for the same rationale.
     *
     * @param world
     * @param id PlotID
     * @return top location of the plot, or a zero Vector when gen manager is missing
     */
    @Deprecated
    public Vector getPlotTopLoc(IWorld world, PlotId id) {
        IPlotMe_GeneratorManager gm = getGenManager(world);
        if (gm == null) {
            return new Vector(0, 0, 0);
        }
        return gm.getPlotTopLoc(id);
    }

    /**
     * Get the x coordinate at the bottom of the plot
     *
     * @param id    plot id
     * @param world
     * @return bottom x coordinate of the plot
     */
    @Deprecated
    public int bottomX(PlotId id, IWorld world) {
        return getGenManager(world).bottomX(id);
    }

    /**
     * Get the x coordinate at the top of the plot
     *
     * @param id    plot id
     * @param world
     * @return top x coordinate of the plot
     */
    @Deprecated
    public int topX(PlotId id, IWorld world) {
        return getGenManager(world).topX(id);
    }

    /**
     * Get the z coordinate at the bottom of the plot
     *
     * @param id    plot id
     * @param world
     * @return bottom z coordinate of the plot
     */
    @Deprecated
    public int bottomZ(PlotId id, IWorld world) {
        return getGenManager(world).bottomZ(id);
    }

    /**
     * Get the z coordinate at the top of the plot
     *
     * @param id    plot id
     * @param world
     * @return top z coordinate of the plot
     */
    @Deprecated
    public int topZ(PlotId id, IWorld world) {
        return getGenManager(world).topZ(id);
    }

    /**
     * Get the plot home location of a plot.
     *
     * Returns null when the world has no registered generator manager.
     * Callers (CmdHome, CmdAuto, CmdTP, CmdDeny) must null-check the
     * result and either send a friendly message or skip the teleport.
     *
     * @param id    plot id to get home of
     * @param world
     * @return an ILocation of the plot home location, or null when gen manager is missing
     */
    public Location getPlotHome(PlotId id, IWorld world) {
        IPlotMe_GeneratorManager gm = getGenManager(world);
        if (gm == null) {
            return null;
        }
        return gm.getPlotHome(id);
    }

    /**
     * Get the players in the Plot
     *
     * @param id    plot id
     * @param world
     * @return a list of players in the plot
     */
    public List<IPlayer> getPlayersInPlot(PlotId id, IWorld world) {
        return getGenManager(world).getPlayersInPlot(id);
    }

    public IPlotMe_GeneratorManager getGenManager(IWorld world) {
        return plugin.getGenManager(world);
    }

    /**
     * Get the number of plots the player owns
     *
     * @param uuid player UUID
     * @param world plotworld
     * @return number of plots the player owns
     */
    public int getOwnedPlotCount(UUID uuid, IWorld world) {
        return plugin.getSqlManager().getPlotCount(world, uuid);
    }

    /**
     * Checks if the plotworld has economy features enabled
     *
     * @param pmi plotmapinfo
     * @return true if economy enabled
     */
    public boolean isEconomyEnabled(PlotMapInfo pmi) {
        if (plugin.getConfig().getBoolean("globalUseEconomy") && plugin.getServerBridge().getEconomy().isPresent()) {
            return pmi.canUseEconomy();
        }
        return false;
    }

    /**
     * Checks if the plotworld has economy features enabled
     *
     * @param world world
     * @return true if economy enabled
     */

    public boolean isEconomyEnabled(IWorld world) {
        PlotMapInfo pmi = getMap(world);
        return isEconomyEnabled(pmi);
    }

    /**
     * Get the PlotMap based on the world given
     * @param world plotworld
     * @return PlotMapInfo for the plotworld, if the world doesn't exist then it will return null
     */
    public PlotMapInfo getMap(IWorld world) {
        return getPlotMaps().get(world);
    }

    /**
     * Get the PlotMap based on the world given
     * @param location the location in a plotworld
     * @return PlotMapInfo for the plotworld, if the world doesn't exist then it will return null
     */
    public PlotMapInfo getMap(Location location) {
        return getMap(location.getWorld());
    }

    /**
     * Get the PlotMap based on the world given
     * @param player a player in a plotworld
     * @return PlotMapInfo for the plotworld, if the world doesn't exist then it will return null
     */
    public PlotMapInfo getMap(IEntity player) {
        return getMap(player.getWorld());
    }

    /**
     * Walks the 8 neighbours of the freshly-claimed plot {@code id} in
     * {@code world}. For every adjacent neighbour that shares an owner with
     * the center plot, fills the road between them with floor blocks (so the
     * two plots visually become one). For every diagonal that is fully
     * surrounded by same-owner plots (the 2x2 corner case), fills the small
     * road-intersection square too.
     *
     * Modernized from the pre-rewrite version that used {@code String} plot
     * ids and looked up plots in a synthetic map. We now:
     *   - take a typed {@link PlotId} and {@link IWorld};
     *   - resolve neighbours through {@link #getPlotById(PlotId, IWorld)} so the
     *     SQL cache is consulted;
     *   - compare ownership by {@link Plot#getOwnerId()} (UUID) instead of
     *     display name, because the old name-based check broke on renames
     *     and case-mismatches;
     *   - call into the generator's road-fill helpers, which only take the
     *     two plot ids (no world arg — they read it off their bound world).
     *
     * Also persists the link by adding each adjacent neighbour to the
     * center's {@link Plot#getMergedWith()} set (and vice versa) and marking
     * both plots dirty.
     */
    public void adjustLinkedPlots(PlotId id, IWorld world) {
        IPlotMe_GeneratorManager genMan = getGenManager(world);
        if (genMan == null) {
            return;
        }

        int x = id.x();
        int z = id.z();

        Plot p11 = getPlotById(id, world);
        if (p11 == null) {
            return;
        }

        Plot p01 = getPlotById(new PlotId(x - 1, z), world);
        Plot p10 = getPlotById(new PlotId(x, z - 1), world);
        Plot p12 = getPlotById(new PlotId(x, z + 1), world);
        Plot p21 = getPlotById(new PlotId(x + 1, z), world);
        Plot p00 = getPlotById(new PlotId(x - 1, z - 1), world);
        Plot p02 = getPlotById(new PlotId(x - 1, z + 1), world);
        Plot p20 = getPlotById(new PlotId(x + 1, z - 1), world);
        Plot p22 = getPlotById(new PlotId(x + 1, z + 1), world);

        if (sameOwner(p01, p11)) {
            genMan.fillRoad(p01.getId(), p11.getId());
            linkMergedPlots(p01, p11);
        }
        if (sameOwner(p10, p11)) {
            genMan.fillRoad(p10.getId(), p11.getId());
            linkMergedPlots(p10, p11);
        }
        if (sameOwner(p12, p11)) {
            genMan.fillRoad(p12.getId(), p11.getId());
            linkMergedPlots(p12, p11);
        }
        if (sameOwner(p21, p11)) {
            genMan.fillRoad(p21.getId(), p11.getId());
            linkMergedPlots(p21, p11);
        }

        // 2x2 corner fills: only when the three other quadrants are also
        // same-owner so we don't carve an isolated diagonal hole through
        // someone else's plot.
        if (sameOwner(p00, p11) && sameOwner(p10, p11) && sameOwner(p01, p11)) {
            genMan.fillMiddleRoad(p00.getId(), p11.getId());
            linkMergedPlots(p00, p11);
        }
        if (sameOwner(p10, p11) && sameOwner(p20, p11) && sameOwner(p21, p11)) {
            genMan.fillMiddleRoad(p20.getId(), p11.getId());
            linkMergedPlots(p20, p11);
        }
        if (sameOwner(p01, p11) && sameOwner(p02, p11) && sameOwner(p12, p11)) {
            genMan.fillMiddleRoad(p02.getId(), p11.getId());
            linkMergedPlots(p02, p11);
        }
        if (sameOwner(p12, p11) && sameOwner(p21, p11) && sameOwner(p22, p11)) {
            genMan.fillMiddleRoad(p22.getId(), p11.getId());
            linkMergedPlots(p22, p11);
        }
    }

    /**
     * Same-owner check that tolerates either side being null and prefers the
     * stable UUID comparison over the legacy display-name match.
     */
    private static boolean sameOwner(Plot a, Plot b) {
        if (a == null || b == null) return false;
        if (a.getOwnerId() == null || b.getOwnerId() == null) {
            return a.getOwner() != null && a.getOwner().equalsIgnoreCase(b.getOwner());
        }
        return a.getOwnerId().equals(b.getOwnerId());
    }

    /**
     * Symmetrically record that {@code a} and {@code b} are merged, and mark
     * both plots dirty so the async flusher writes the new link rows. Safe
     * to call when the two plots are already linked (the underlying set
     * dedupes).
     */
    public void linkMergedPlots(Plot a, Plot b) {
        if (a == null || b == null || a == b) {
            return;
        }
        a.addMergedWith(b.getId());
        b.addMergedWith(a.getId());
        plugin.getSqlManager().markDirty(a);
        plugin.getSqlManager().markDirty(b);
    }


    /**
     * Gets the plot with the given id in the given world.
     *
     * @param id plot id
     * @param world
     * @return plot
     */
    public Plot getPlotById(PlotId id, IWorld world) {
        if (world == null || id == null) {
            return null;
        }
        return plugin.getSqlManager().getPlot(id, world);
    }

    /**
     * Plot to add to loaded plotmap.
     *  @param plot Plot to be added
     */
    public void loadPlot(Plot plot) {
        PlotLoadEvent event = new PlotLoadEvent(plot);
        plugin.getEventBus().post(event);

    }

    /**
     * Get the first plotworld defined in config.
     *
     * Returns null when no plot worlds are registered (e.g. a fresh server
     * before the admin has created a plot world). Callers MUST null-check
     * the result and tell the user instead of indexing into the result.
     *
     * Previous implementation indexed [0] of the keySet array, throwing
     * ArrayIndexOutOfBoundsException on empty servers — this surfaced
     * whenever a player ran /plotme (no-args), which routes through
     * CmdShowHelp and reads the "first world" to print plot-limit info.
     *
     * @return first plotworld, or null when no plotworlds are registered
     */
    public IWorld getFirstWorld() {
        Set<IWorld> iWorlds = getPlotMaps().keySet();
        if (iWorlds.isEmpty()) {
            return null;
        }
        return iWorlds.iterator().next();
    }

    /**
     * Checks if world is a PlotWorld
     *
     * @param world object to get the location from
     * @return true if world is plotworld, false otherwise
     */
    public boolean isPlotWorld(IWorld world) {
        return getPlotMaps().containsKey(world);
    }

    /**
     * Checks if location is a PlotWorld
     *
     * @param location location to be checked
     * @return true if world is plotworld, false otherwise
     */
    public boolean isPlotWorld(Location location) {
        return isPlotWorld(location.getWorld());
    }

    /**
     * Checks if the entity is in a plotworld
     *
     * @param entity entity to get the location from
     * @return true if world is plotworld, false otherwise
     */
    public boolean isPlotWorld(IEntity entity) {
        return isPlotWorld(entity.getWorld());
    }

    /**
     * Creates a new plot
     *
     * @param id    plot id
     * @param world
     * @param owner owner name
     * @param uuid  owner uuid
     * @param pmi   plotmap to add the plot to    @return the new plot created
     *
     * @throws NullPointerException If the <code>id</code> argument is <code>null</code>
     */
    public Plot createPlot(PlotId id, IWorld world, String owner, UUID uuid, PlotMapInfo pmi) {

        Plot plot = new Plot(owner, uuid, world, id, this.getPlotTopLoc(world, id), this.getPlotBottomLoc(world, id));
        if (pmi.getDaysToExpiration() == 0) {
            plot.setExpiredDate(null);
        } else {
            plot.setExpiredDate(LocalDate.now().plusDays(pmi.getDaysToExpiration()));
        }


        setOwnerSign(plot);
        loadPlot(plot);
        adjustWall(plot, true);

        plugin.getSqlManager().addPlot(plot);
        return plot;
    }

    /**
     * Move a plot from one location to another
     *
     *
     * @param world
     * @param idFrom the id of the plot to be moved
     * @param idTo   the id the plot will be moved to
     * @return true if successful, false otherwise
     */
    public boolean movePlot(IWorld world, PlotId idFrom, PlotId idTo) {

        if (!getGenManager(world).movePlot(idFrom, idTo)) {
            return false;
        }

        Plot plotFrom = getPlotById(idFrom, world);
        Plot plotTo = getPlotById(idTo, world);

        if (plotFrom != null) {
            if (plotTo != null) {
                deletePlot(plotFrom);
                deletePlot(plotTo);
                plotTo.setId(idFrom);
                plugin.getSqlManager().addPlot(plotTo);
                loadPlot(plotTo);

                plotFrom.setId(idTo);
                plugin.getSqlManager().addPlot(plotFrom);
                loadPlot(plotFrom);

                setOwnerSign(plotFrom);
                setOwnerSign(plotTo);
            } else {
                movePlotToEmpty(plotFrom, idTo);
            }
        } else if (plotTo != null) {
            movePlotToEmpty(plotTo, idFrom);
        }

        return true;
    }

    /**
     * Move a plot to an spot where there is no plot existing.
     */
    private void movePlotToEmpty(Plot filledPlot, PlotId idDestination) {
        deletePlot(filledPlot);

        filledPlot.setId(idDestination);
        plugin.getSqlManager().addPlot(filledPlot);
        loadPlot(filledPlot);

        setOwnerSign(filledPlot);
        setSellSign(filledPlot);
    }

    /**
     * Clears a plot
     *  @param plot   the plot to be cleared
     * @param sender the sender of the command
     * @param reason The reason they will be cleared. The cause can be: EXPIRED, RESET, CLEAR
     */
    public void clear(Plot plot, ICommandSender sender, ClearReason reason) {
        getGenManager(plot.getWorld()).clearEntities(plot.getPlotBottomLoc(), plot.getPlotTopLoc());
        if (reason.equals(ClearReason.Clear)) {
            adjustWall(plot, true);
        } else {
            adjustWall(plot, false);
        }
        plugin.addPlotToClear(plot, reason, sender);
    }

    /**
     * Checks if the plot is claimed or not
     *
     * @param id    the plot id to be checked
     * @param world
     * @return true if the plot is unclaimed, false otherwise
     */
    public boolean isPlotAvailable(PlotId id, IWorld world) {
        // Belt + suspenders: CmdAuto's spiral search runs asynchronously
        // and there is a window between `/mv create plots normal -g PlotMe`
        // succeeding and our WorldInitEvent/WorldLoadEvent hook actually
        // registering the gen manager. Without this guard we would NPE
        // inside getPlotTopLoc below (PlotMeCoreManager.java line ~236).
        //
        // No gen manager => we don't know the plot geometry for this world,
        // so no plot can be safely auto-claimed. Caller (CmdAuto) should
        // already have bailed earlier with MsgNoPlotWorldSetup, but if a
        // future caller misses that check we fail closed here.
        if (getGenManager(world) == null) {
            return false;
        }
        HashMap<PlotId, Plot> worldPlots = plugin.getSqlManager().plots.get(world);
        if (worldPlots != null && worldPlots.containsKey(id)) {
            return false;
        }
        if (getPlotTopLoc(world, id).getX() > world.getWorldBorder().minX()) {
            if (getPlotBottomLoc(world, id).getX() < world.getWorldBorder().maxX()) {
                if (getPlotTopLoc(world, id).getZ() > world.getWorldBorder().minZ()) {
                    if (getPlotBottomLoc(world, id).getZ() < world.getWorldBorder().maxZ()) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    /**
     * Updates the blocks on the plot border
     *
     * @param player the player in the plot
     */
    public void adjustWall(IPlayer player) {
        Plot plot = getPlot(player);
        if (plot == null) {
            player.sendMessage(plugin.C("NoPlotFound"));
        } else {
            getGenManager(player.getWorld()).adjustPlotFor(plot, true, plot.isProtected(), plot.isForSale());
        }
    }

    /**
     * Updates the blocks on the plot border
     * @param plot      plot id
     * @param claimed is the plot claimed
     */
    public void adjustWall(Plot plot, boolean claimed) {
        getGenManager(plot.getWorld()).adjustPlotFor(plot, claimed, plot.isProtected(), plot.isForSale());
    }

    /**
     * Paint the plot's biome across every block of every plot in the merged
     * cluster {@code plot} belongs to. Standalone plot = just that plot's
     * XZ rectangle; merged cluster = the union of all linked plots'
     * rectangles. Returns the total block-column count touched so callers
     * can show a meaningful "N blocks" confirmation to the player.
     */
    public int setBiome(Plot plot) {
        IPlotMe_GeneratorManager gm = getGenManager(plot.getWorld());
        String biome = plot.getBiome();
        int total = gm.setBiome(plot.getId(), biome);
        // Apply to merged neighbours too AND the road strips between them —
        // when plots are merged the roads visually belong to the owner, so
        // biome must follow.
        java.util.Set<Plot> clusterPlots = getMergedCluster(plot);
        java.util.Set<PlotId> cluster = new java.util.HashSet<>();
        for (Plot p : clusterPlots) cluster.add(p.getId());
        for (PlotId other : cluster) {
            if (!other.equals(plot.getId())) {
                total += gm.setBiome(other, biome);
            }
        }
        // Road strips: for every orthogonally-adjacent pair of cluster members
        // re-paint the strip between them. For every 2x2 sub-cluster also re-
        // paint the center intersection.
        java.util.List<PlotId> members = new java.util.ArrayList<>(cluster);
        for (int i = 0; i < members.size(); i++) {
            PlotId a = members.get(i);
            for (int j = i + 1; j < members.size(); j++) {
                PlotId b = members.get(j);
                int dx = Math.abs(a.x() - b.x());
                int dz = Math.abs(a.z() - b.z());
                if (dx + dz == 1) { // edge-adjacent
                    total += setBiomeOnRoadStrip(gm, plot.getWorld(), a, b, biome);
                }
            }
        }
        // 2x2 centers: for every member, if its +x, +z, +x+z neighbours are
        // all in the cluster, paint the path-width center.
        for (PlotId nw : members) {
            PlotId ne = new PlotId(nw.x() + 1, nw.z());
            PlotId sw = new PlotId(nw.x(),     nw.z() + 1);
            PlotId se = new PlotId(nw.x() + 1, nw.z() + 1);
            if (cluster.contains(ne) && cluster.contains(sw) && cluster.contains(se)) {
                total += setBiomeOnCenterIntersection(gm, plot.getWorld(), nw, ne, sw, se, biome);
            }
        }
        return total;
    }

    private int setBiomeOnRoadStrip(IPlotMe_GeneratorManager gm, IWorld world,
                                     PlotId a, PlotId b, String biome) {
        Vector botA = gm.getPlotBottomLoc(a), topA = gm.getPlotTopLoc(a);
        Vector botB = gm.getPlotBottomLoc(b), topB = gm.getPlotTopLoc(b);
        int minX, maxX, minZ, maxZ;
        if (botA.getBlockX() == botB.getBlockX()) {
            // N-S strip between same X column
            minX = botA.getBlockX();
            maxX = topA.getBlockX();
            minZ = Math.min(botA.getBlockZ(), botB.getBlockZ()) + gm.getPlotSize();
            maxZ = Math.max(topA.getBlockZ(),    topB.getBlockZ())    - gm.getPlotSize();
        } else {
            // E-W strip between same Z row
            minZ = botA.getBlockZ();
            maxZ = topA.getBlockZ();
            minX = Math.min(botA.getBlockX(), botB.getBlockX()) + gm.getPlotSize();
            maxX = Math.max(topA.getBlockX(),    topB.getBlockX())    - gm.getPlotSize();
        }
        return gm.setBiomeRegion(world, minX, maxX, minZ, maxZ, biome);
    }

    private int setBiomeOnCenterIntersection(IPlotMe_GeneratorManager gm, IWorld world,
                                              PlotId nw, PlotId ne, PlotId sw, PlotId se,
                                              String biome) {
        Vector topNW    = gm.getPlotTopLoc(nw);
        Vector bottomSE = gm.getPlotBottomLoc(se);
        int minX = topNW.getBlockX() + 1;
        int maxX = bottomSE.getBlockX() - 1;
        int minZ = topNW.getBlockZ() + 1;
        int maxZ = bottomSE.getBlockZ() - 1;
        if (minX > maxX || minZ > maxZ) return 0;
        return gm.setBiomeRegion(world, minX, maxX, minZ, maxZ, biome);
    }


    /**
     * Gets all the players that can use WorldEdit Anywhere in plotworld
     *
     * @return a list of the uuid's of players able to WorldEdit Anywhere
     */
    public HashSet<UUID> getPlayersIgnoringWELimit() {
        return playersignoringwelimit;
    }

    /**
     * Gives a user the ability to use WorldEdit anywhere in plotworld
     *
     * @param uuid uuid of the player
     */
    public void addPlayerIgnoringWELimit(UUID uuid) {
        getPlayersIgnoringWELimit().add(uuid);
    }

    /**
     * Removes the ability for a user to use WorldEdit anywhere in plotworld
     *
     * @param uuid uuid of the player
     */
    public void removePlayerIgnoringWELimit(UUID uuid) {
        getPlayersIgnoringWELimit().remove(uuid);
    }


    /**
     * Gets the active plotworlds
     *
     * @return the active plotworlds
     */
    public HashMap<IWorld, PlotMapInfo> getPlotMaps() {
        return plotmaps;
    }

    /**
     * Register the plotworld the plotmap
     *  @param world name of a plotworld
     * @param map   {@link PlotMapInfo} information
     */
    public void addPlotMap(IWorld world, PlotMapInfo map) {
        getPlotMaps().put(world, map);
    }


    public boolean isPlayerIgnoringWELimit(IPlayer player) {
        if (plugin.getConfig().getBoolean("defaultWEAnywhere") && player.hasPermission(PermissionNames.ADMIN_WEANYWHERE)) {
            return !getPlayersIgnoringWELimit().contains(player.getUniqueId());
        } else {
            return getPlayersIgnoringWELimit().contains(player.getUniqueId());
        }
    }

    /**
     * Gets the location of the middle of the plot
     *
     * @param world plotworld
     * @param id    plot id
     * @return location as an ILocation
     */
    public Vector getPlotMiddle(IWorld world, PlotId id) {
        return getGenManager(world).getPlotMiddle(id);
    }

    public void UpdatePlayerNameFromId(final UUID uuid, final String name) {
        for (final Plot plot : plugin.getSqlManager().getPlayerPlots(uuid)) {
            setOwnerSign(plot);
        }
    }


    public IOfflinePlayer getPlayer(String name) {
        return plugin.getServerBridge().getPlayer(name);
    }

    public Plot getPlot(Location location) {
        PlotId id = getPlotId(location);
        if (id == null) {
            return null;
        }
        return getPlotById(id, location.getWorld());
    }

    public Plot getPlot(IPlayer player) {
        PlotId id = getPlotId(player);
        if (id == null) {
            return null;
        }
        return getPlotById(id, player.getWorld());
    }

    /**
     * Like {@link #getPlot(IPlayer)}, but if the player is standing on the
     * road strip that was filled in by a merge, return the merged plot that
     * owns that strip. The straight-road case requires the two plots on either
     * side of the strip to be in the same merged cluster; the 4-way
     * intersection case requires all four corner plots to be in the same
     * cluster (mirroring the {@code adjustLinkedPlots} fill criteria).
     *
     * Returns {@code null} if the player is on a plain unclaimed road (no
     * merge connects across the strip), preserving the original "not on a
     * plot" behaviour.
     *
     * Strategy when {@link #getPlotId(IPlayer)} returns null:
     *  - probe outward in +X / -X / +Z / -Z from the player's block position
     *    in growing steps until each direction either finds a plot id or
     *    runs out of budget (capped at {@code plotSize} blocks — a road is
     *    always narrower than a plot);
     *  - collect the unique non-null neighbour ids;
     *  - if exactly two ids are found across one axis, treat it as a straight
     *    road strip and require those two plots to be merged;
     *  - if four ids are found (intersection center), require all four to be
     *    members of the same merged cluster;
     *  - otherwise, the strip is not part of a merged claim and we return
     *    {@code null}.
     */
    public Plot getPlotOrMergedRoad(IPlayer player) {
        if (player == null) {
            return null;
        }
        Plot direct = getPlot(player);
        if (direct != null) {
            return direct;
        }
        return getPlotOrMergedRoad(player.getLocation());
    }

    /**
     * {@link Location}-based variant of {@link #getPlotOrMergedRoad(IPlayer)}.
     * Used by protection listeners (block place/break) so a block on a
     * merged-road strip resolves to its cluster's plot for ownership checks.
     * Same fallback semantics: returns the direct plot if the location is on
     * one, otherwise probes the four cardinal directions for the flanking
     * plots and accepts the cluster only if the strip really is merge-filled
     * (both sides of a straight road, or all four corners of an
     * intersection). Returns {@code null} for an unclaimed road, preserving
     * the original "not in any plot → deny build" behaviour upstream of the
     * caller.
     */
    public Plot getPlotOrMergedRoad(Location loc) {
        if (loc == null) {
            return null;
        }
        Plot direct = getPlot(loc);
        if (direct != null) {
            return direct;
        }
        IWorld world = loc.getWorld();
        if (world == null) {
            return null;
        }
        IPlotMe_GeneratorManager genMan = getGenManager(world);
        if (genMan == null) {
            return null;
        }
        Vector base = loc.getVector();
        // Probe from the player's block-aligned position. internalgetPlotId
        // reads BlockX/BlockZ off the vector, and Vector.getBlockX uses
        // Math.round (not floor) — passing the already-rounded coordinates
        // avoids off-by-one when the player is mid-block.
        int baseX = base.getBlockX();
        int baseY = base.getBlockY();
        int baseZ = base.getBlockZ();
        int plotSize = genMan.getPlotSize();
        // Cap probing at plotSize blocks: roads are guaranteed narrower than a
        // plot, so if we haven't found a plot by then there is nothing useful
        // out that direction.
        int budget = Math.max(1, plotSize);

        PlotId west  = probeForPlotId(genMan, baseX, baseY, baseZ, -1, 0, budget);
        PlotId east  = probeForPlotId(genMan, baseX, baseY, baseZ, +1, 0, budget);
        PlotId north = probeForPlotId(genMan, baseX, baseY, baseZ, 0, -1, budget);
        PlotId south = probeForPlotId(genMan, baseX, baseY, baseZ, 0, +1, budget);

        boolean xAxisRoad = west != null && east != null;
        boolean zAxisRoad = north != null && south != null;

        // 4-way intersection: the small square in the center of a 2x2 plot
        // cluster. All four corner plots must belong to one merged cluster.
        if (xAxisRoad && zAxisRoad) {
            // The four corners around an intersection are (west.x, north.z),
            // (east.x, north.z), (west.x, south.z), (east.x, south.z).
            PlotId nw = new PlotId(west.x(),  north.z());
            PlotId ne = new PlotId(east.x(),  north.z());
            PlotId sw = new PlotId(west.x(),  south.z());
            PlotId se = new PlotId(east.x(),  south.z());
            Plot pNW = getPlotById(nw, world);
            Plot pNE = getPlotById(ne, world);
            Plot pSW = getPlotById(sw, world);
            Plot pSE = getPlotById(se, world);
            if (pNW != null && pNE != null && pSW != null && pSE != null) {
                java.util.Set<Plot> cluster = getMergedCluster(pNW);
                if (cluster.contains(pNE) && cluster.contains(pSW) && cluster.contains(pSE)) {
                    return pNW;
                }
            }
            return null;
        }

        // Straight road strip along the X axis (between west and east plots
        // sharing the same z). The two probe results should both resolve to
        // real plots and be in the same merged cluster.
        if (xAxisRoad) {
            Plot pW = getPlotById(west, world);
            Plot pE = getPlotById(east, world);
            if (pW != null && pE != null && getMergedCluster(pW).contains(pE)) {
                return pW;
            }
            return null;
        }
        // Straight road strip along the Z axis (between north and south plots
        // sharing the same x).
        if (zAxisRoad) {
            Plot pN = getPlotById(north, world);
            Plot pS = getPlotById(south, world);
            if (pN != null && pS != null && getMergedCluster(pN).contains(pS)) {
                return pN;
            }
            return null;
        }
        return null;
    }

    /**
     * Walk outward from the block-aligned base position along the (dx, dz)
     * unit step in growing 1-block increments, calling {@code
     * genMan.getPlotId} on each step. Returns the first non-null PlotId
     * encountered, or {@code null} if the budget is exhausted without finding
     * one. Used by {@link #getPlotOrMergedRoad(IPlayer)} to discover the
     * plots flanking a road strip without needing the generator's internal
     * path-width.
     */
    private static PlotId probeForPlotId(IPlotMe_GeneratorManager genMan, int baseX, int baseY, int baseZ, int dx, int dz, int budget) {
        for (int step = 1; step <= budget; step++) {
            Vector probe = new Vector(baseX + dx * step, baseY, baseZ + dz * step);
            PlotId pid = genMan.getPlotId(probe);
            if (pid != null) {
                return pid;
            }
        }
        return null;
    }

    public IWorld getWorld(String world) {
        for (IWorld iw : getPlotMaps().keySet()) {
            if (iw.getName().equalsIgnoreCase(world)) {
                return iw;
            }
        }
        return null;
    }

    /**
     * Map a cardinal direction string ("north"/"south"/"east"/"west") to the
     * PlotId offset that lands on the neighbour on that side. Returns {@code
     * null} for unknown inputs. World axis: -Z = north, +Z = south, +X = east,
     * -X = west (standard Minecraft compass).
     */
    public static PlotId neighbourId(PlotId from, String direction) {
        if (from == null || direction == null) {
            return null;
        }
        switch (direction.toLowerCase(java.util.Locale.ROOT)) {
            case "north": return new PlotId(from.x(), from.z() - 1);
            case "south": return new PlotId(from.x(), from.z() + 1);
            case "east":  return new PlotId(from.x() + 1, from.z());
            case "west":  return new PlotId(from.x() - 1, from.z());
            default: return null;
        }
    }

    /**
     * Flood-fills the cluster of plots reachable from {@code start} through
     * {@link Plot#getMergedWith()} links. Useful for callers (protection,
     * clear, info) that want to treat a merged cluster as a single logical
     * plot, even though each member still has its own row.
     *
     * TODO: wire this into protect/clear/etc. once we've defined the
     * "merged cluster acts as one big plot" semantics. For now it's just
     * available as data and the only consumer is /plotme info / debug.
     */
    public java.util.Set<Plot> getMergedCluster(Plot start) {
        java.util.LinkedHashSet<Plot> visited = new java.util.LinkedHashSet<>();
        if (start == null) {
            return visited;
        }
        java.util.ArrayDeque<Plot> queue = new java.util.ArrayDeque<>();
        queue.add(start);
        IWorld world = start.getWorld();
        while (!queue.isEmpty()) {
            Plot cur = queue.poll();
            if (cur == null || !visited.add(cur)) continue;
            for (PlotId neighbour : cur.getMergedWith()) {
                Plot next = getPlotById(neighbour, world);
                if (next != null && !visited.contains(next)) {
                    queue.add(next);
                }
            }
        }
        return visited;
    }

    /**
     * Merge two adjacent plots: links them symmetrically in
     * {@link Plot#getMergedWith()} and asks the generator to fill the road
     * between them. Caller is expected to have already validated ownership
     * and adjacency. Returns {@code true} on success.
     *
     * After the pair is linked, also looks at the freshly-formed cluster for
     * any 2x2 of merged plots: each such 2x2 has a path-width x path-width
     * central intersection where four roads meet, which is not covered by
     * the edge-to-edge fillRoad pass and would otherwise stay as the
     * original road pattern (unclaimed-wall slabs around a sand floor). For
     * every 2x2 detected, {@code fillCenterIntersection} is called to wipe
     * the centre square with the plot-floor treatment, and the four corner
     * plots are linked diagonally so the cluster is a single connected
     * graph (otherwise getMergedCluster would not include diagonal members
     * if a player merges in an L-shape and never adds the diagonal pair).
     *
     * Finally refreshes the cluster's owner sign so only the outermost-NW
     * member carries a sign and that sign holds the joined cluster id list
     * on line 4.
     */
    public boolean mergePlots(Plot a, Plot b) {
        if (a == null || b == null || a == b) {
            return false;
        }
        IWorld world = a.getWorld();
        if (world == null || !world.equals(b.getWorld())) {
            return false;
        }
        IPlotMe_GeneratorManager genMan = getGenManager(world);
        if (genMan == null) {
            return false;
        }
        genMan.fillRoad(a.getId(), b.getId());
        linkMergedPlots(a, b);

        // 2x2 center-intersection sweep over the cluster. After linking a/b
        // (and any prior pair-merges) we walk the cluster and, for every NW
        // plot in it, check whether the NE/SW/SE neighbours are also
        // members. If they are, the four-way intersection between them
        // needs to be cleaned.
        java.util.Set<Plot> cluster = getMergedCluster(a);
        for (Plot p : new java.util.ArrayList<>(cluster)) {
            PlotId nwId = p.getId();
            PlotId neId = new PlotId(nwId.x() + 1, nwId.z());
            PlotId swId = new PlotId(nwId.x(),     nwId.z() + 1);
            PlotId seId = new PlotId(nwId.x() + 1, nwId.z() + 1);
            Plot ne = getPlotById(neId, world);
            Plot sw = getPlotById(swId, world);
            Plot se = getPlotById(seId, world);
            if (ne == null || sw == null || se == null) continue;
            // All four must be in the same cluster (i.e. transitively
            // reachable from p). cluster came from getMergedCluster(a), and
            // a/b are now linked, but ne/sw/se may belong to a different
            // pair-merge that hasn't been joined to a's cluster yet —
            // verify membership by checking the cluster set.
            if (!cluster.contains(ne) || !cluster.contains(sw) || !cluster.contains(se)) {
                continue;
            }
            genMan.fillCenterIntersection(nwId, neId, swId, seId);
            // Link diagonals so subsequent getMergedCluster calls (e.g.
            // for sign refresh) see one connected component.
            linkMergedPlots(p, se);
            linkMergedPlots(ne, sw);
        }

        // Sign refresh: outermost-NW gets the cluster sign, the rest get
        // their per-plot signs cleared. Done last so the cluster set above
        // already reflects the new diagonal links.
        refreshClusterOwnerSign(a);
        return true;
    }

}