package com.worldcretornica.plotme.defaultgenerator;

import com.worldcretornica.plotme_core.Plot;
import com.worldcretornica.plotme_core.PlotId;
import com.worldcretornica.plotme_core.api.IBlock;
import com.worldcretornica.plotme_core.api.IWorld;
import com.worldcretornica.plotme_core.api.Location;
import com.worldcretornica.plotme_core.api.Vector;
import com.worldcretornica.plotme_core.bukkit.api.BukkitWorld;
import com.worldcretornica.plotme_core.utils.ChunkCoords;
import com.worldcretornica.plotme_core.utils.ChunkEntry;
import com.worldcretornica.plotme_core.utils.ClearEntry;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Sign;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.WallSign;
import org.bukkit.block.sign.Side;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Plot geometry + per-plot mutations (clear, wall recolor, info signs)
 * for the merged default generator. Ported to Material+BlockData; the
 * old short/byte id pipeline is gone.
 */
public class DefaultPlotManager extends AbstractGenManager {

    public DefaultPlotManager(ConfigurationSection wgc, IWorld world) {
        super(wgc, world);
    }

    @Override
    public PlotId getPlotId(Vector loc) {
        int posx = loc.getBlockX();
        int posz = loc.getBlockZ();
        int pathSize = getPathWidth();
        int size = getPlotSize() + pathSize;
        return internalgetPlotId(pathSize, size, posx, posz);
    }

    @Override
    public void fillRoad(PlotId id1, PlotId id2) {
        Vector bottomPlot1 = getPlotBottomLoc(id1);
        Vector topPlot1    = getPlotTopLoc(id1);
        Vector bottomPlot2 = getPlotBottomLoc(id2);
        Vector topPlot2    = getPlotTopLoc(id2);

        int minX, maxX, minZ, maxZ;
        int h = getGroundHeight();

        // After a merge, the leftover road strip should look like the owner's
        // plot, not like an unclaimed border: use the claimed WALL_BLOCK for
        // the outer edge walls and PLOT_FLOOR_BLOCK for the top surface so the
        // strip matches the rest of the merged plot.
        BlockData wall  = MaterialParser.parseBlockData(wgc.getString(DefaultWorldConfigPath.WALL_BLOCK.key(),       "44"));
        BlockData floor = MaterialParser.parseBlockData(wgc.getString(DefaultWorldConfigPath.PLOT_FLOOR_BLOCK.key(), "2"));
        BlockData fill  = MaterialParser.parseBlockData(wgc.getString(DefaultWorldConfigPath.FILL_BLOCK.key(),       "3"));

        if (bottomPlot1.getBlockX() == bottomPlot2.getBlockX()) {
            minX = bottomPlot1.getBlockX();
            maxX = topPlot1.getBlockX();
            minZ = Math.min(bottomPlot1.getBlockZ(), bottomPlot2.getBlockZ()) + getPlotSize();
            maxZ = Math.max(topPlot1.getBlockZ(),    topPlot2.getBlockZ())    - getPlotSize();
        } else {
            minZ = bottomPlot1.getBlockZ();
            maxZ = topPlot1.getBlockZ();
            minX = Math.min(bottomPlot1.getBlockX(), bottomPlot2.getBlockX()) + getPlotSize();
            maxX = Math.max(topPlot1.getBlockX(),    topPlot2.getBlockX())    - getPlotSize();
        }

        boolean isWallX = (maxX - minX) > (maxZ - minZ);
        if (isWallX) { minX--; maxX++; } else { minZ--; maxZ++; }

        World w = ((BukkitWorld) world).getWorld();
        int topY = w.getMaxHeight();

        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int y = h; y < topY; y++) {
                    if (y >= (h + 2)) {
                        w.getBlockAt(x, y, z).setType(Material.AIR, false);
                    } else if (y == (h + 1)) {
                        if (isWallX && (x == minX || x == maxX) || !isWallX && (z == minZ || z == maxZ)) {
                            w.getBlockAt(x, y, z).setBlockData(wall, false);
                        } else {
                            w.getBlockAt(x, y, z).setType(Material.AIR, false);
                        }
                    } else if (y == h) {
                        // Top surface of the filled-in road -> plot floor (grass),
                        // not dirt: the merged strip must blend with the plot.
                        w.getBlockAt(x, y, z).setBlockData(floor, false);
                    } else {
                        w.getBlockAt(x, y, z).setBlockData(fill, false);
                    }
                }
            }
        }
    }

    @Override
    public void fillMiddleRoad(PlotId id1, PlotId id2) {
        Vector bottomPlot1 = getPlotBottomLoc(id1);
        Vector topPlot1    = getPlotTopLoc(id1);
        Vector bottomPlot2 = getPlotBottomLoc(id2);
        Vector topPlot2    = getPlotTopLoc(id2);

        int height = getGroundHeight();
        BlockData floor = MaterialParser.parseBlockData(
                wgc.getString(DefaultWorldConfigPath.PLOT_FLOOR_BLOCK.key(), "2"));
        BlockData fill = MaterialParser.parseBlockData(
                wgc.getString(DefaultWorldConfigPath.FILL_BLOCK.key(), "3"));

        int minX = Math.min(topPlot1.getBlockX(),    topPlot2.getBlockX());
        int maxX = Math.max(bottomPlot1.getBlockX(), bottomPlot2.getBlockX());
        int minZ = Math.min(topPlot1.getBlockZ(),    topPlot2.getBlockZ());
        int maxZ = Math.max(bottomPlot1.getBlockZ(), bottomPlot2.getBlockZ());

        World w = ((BukkitWorld) world).getWorld();
        int topY = w.getMaxHeight();

        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int y = height; y < topY; y++) {
                    if (y >= (height + 1)) {
                        w.getBlockAt(x, y, z).setType(Material.AIR, false);
                    } else if (y == height) {
                        // Top surface -> plot floor (grass).
                        w.getBlockAt(x, y, z).setBlockData(floor, false);
                    } else {
                        // Deep column -> regular fill (dirt) so the merged
                        // corner column matches the rest of the plot.
                        w.getBlockAt(x, y, z).setBlockData(fill, false);
                    }
                }
            }
        }
    }

    /**
     * Clear the central path-width x path-width square where four roads meet
     * in a 2x2 merged cluster. {@link #fillRoad} only handles edge-to-edge
     * strips, and {@link #fillMiddleRoad} only fires when the 2x2 sits inside
     * a 3x3 of same-owner plots — so when the player builds the cluster as
     * four discrete pair-merges, the centre never gets touched and you end up
     * with an island of unclaimed-wall slabs around a sand floor in the
     * middle. This fills that square with the same y-layered treatment as
     * fillRoad: AIR above h+1, AIR at h+1 (no slabs), PLOT_FLOOR at h.
     */
    @Override
    public void fillCenterIntersection(PlotId nw, PlotId ne, PlotId sw, PlotId se) {
        // Geometry: the centre square sits between the top corner of the NW
        // plot and the bottom corner of the SE plot (path-width on each
        // side). Use NW.top+1 .. SE.bottom-1 so we only touch road blocks and
        // leave the surrounding plot cells alone (those are already covered
        // by fillRoad for the adjacent strips).
        Vector topNW    = getPlotTopLoc(nw);
        Vector bottomSE = getPlotBottomLoc(se);
        int minX = topNW.getBlockX() + 1;
        int maxX = bottomSE.getBlockX() - 1;
        int minZ = topNW.getBlockZ() + 1;
        int maxZ = bottomSE.getBlockZ() - 1;
        if (minX > maxX || minZ > maxZ) {
            return; // degenerate (no road between the plots — shouldn't happen)
        }

        int h = getGroundHeight();
        BlockData floor = MaterialParser.parseBlockData(
                wgc.getString(DefaultWorldConfigPath.PLOT_FLOOR_BLOCK.key(), "2"));
        BlockData fill = MaterialParser.parseBlockData(
                wgc.getString(DefaultWorldConfigPath.FILL_BLOCK.key(), "3"));

        World w = ((BukkitWorld) world).getWorld();
        int topY = w.getMaxHeight();
        int minY = w.getMinHeight();

        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                // Clear from ground+1 up to world top: kill any wall slabs or
                // stray blocks left by the original road pattern.
                for (int y = h + 1; y < topY; y++) {
                    w.getBlockAt(x, y, z).setType(Material.AIR, false);
                }
                // Top surface = plot floor (grass), matches the rest of the
                // merged plot.
                w.getBlockAt(x, h, z).setBlockData(floor, false);
                // Sub-surface = regular fill (dirt) so the column matches the
                // surrounding plot floor below the grass layer. fillRoad has
                // dead code for this branch (loop starts at y=h), so we do
                // the column explicitly here from h-1 down to minY+1.
                for (int y = h - 1; y > minY; y--) {
                    w.getBlockAt(x, y, z).setBlockData(fill, false);
                }
            }
        }
    }

    /**
     * Geometric inverse of {@link #fillRoad(PlotId, PlotId)}. Restores the
     * original unclaimed road strip between {@code id1} and {@code id2}:
     *  - AIR above ground+1
     *  - UNCLAIMED_WALL at ground+1 on the two perpendicular caps (the road-
     *    grid borders the strip butts into) AND on the two lateral edges
     *    facing each plot (so the disposed plot AND the still-merged
     *    neighbour both regain their perimeter wall along the strip)
     *  - ROAD_MAIN_BLOCK at y=ground (replacing the PLOT_FLOOR_BLOCK that
     *    fillRoad had laid)
     *  - FILL_BLOCK below ground
     *
     * Called by {@link com.worldcretornica.plotme_core.PlotMeCoreManager#disposeMergedPlot}
     * for every neighbour of the plot being disposed.
     */
    @Override
    public void rebuildRoad(PlotId id1, PlotId id2) {
        Vector bottomPlot1 = getPlotBottomLoc(id1);
        Vector topPlot1    = getPlotTopLoc(id1);
        Vector bottomPlot2 = getPlotBottomLoc(id2);
        Vector topPlot2    = getPlotTopLoc(id2);

        int minX, maxX, minZ, maxZ;
        if (bottomPlot1.getBlockX() == bottomPlot2.getBlockX()) {
            minX = bottomPlot1.getBlockX();
            maxX = topPlot1.getBlockX();
            minZ = Math.min(bottomPlot1.getBlockZ(), bottomPlot2.getBlockZ()) + getPlotSize();
            maxZ = Math.max(topPlot1.getBlockZ(),    topPlot2.getBlockZ())    - getPlotSize();
        } else {
            minZ = bottomPlot1.getBlockZ();
            maxZ = topPlot1.getBlockZ();
            minX = Math.min(bottomPlot1.getBlockX(), bottomPlot2.getBlockX()) + getPlotSize();
            maxX = Math.max(topPlot1.getBlockX(),    topPlot2.getBlockX())    - getPlotSize();
        }
        boolean isWallX = (maxX - minX) > (maxZ - minZ);
        if (isWallX) { minX--; maxX++; } else { minZ--; maxZ++; }

        paintRoadRegion(minX, maxX, minZ, maxZ);
    }

    /**
     * Geometric inverse of {@link #fillCenterIntersection(PlotId, PlotId, PlotId, PlotId)}
     * — restores the path-width x path-width centre square of a 2x2 cluster
     * back to its original road pattern.
     *
     * Within the centre square the original chunk generator places NO wall
     * slabs (those sit on the plot-edge rows just outside this square; they
     * are restored by {@link #rebuildRoad} of the four flanking strips and
     * by {@link #adjustPlotFor} on the remaining plots once their internal-
     * edge skip flags flip off). So the centre is uniform:
     *   y > h+1   -> AIR
     *   y == h+1  -> AIR
     *   y == h    -> ROAD_MAIN_BLOCK
     *   y <  h    -> FILL_BLOCK
     */
    @Override
    public void rebuildCenterIntersection(PlotId nw, PlotId ne, PlotId sw, PlotId se) {
        Vector topNW    = getPlotTopLoc(nw);
        Vector bottomSE = getPlotBottomLoc(se);
        int minX = topNW.getBlockX() + 1;
        int maxX = bottomSE.getBlockX() - 1;
        int minZ = topNW.getBlockZ() + 1;
        int maxZ = bottomSE.getBlockZ() - 1;
        if (minX > maxX || minZ > maxZ) {
            return; // degenerate
        }
        paintRoadRegion(minX, maxX, minZ, maxZ);
    }

    /**
     * Paint an XZ region back to the original chunk-generator road pattern.
     * Mirrors {@link DefaultChunkGenerator#generateNoise} block-for-block at
     * y == h and y == h+1, AIR above, FILL below — so a rebuilt strip is
     * indistinguishable from a freshly-generated road, including the
     * decorative ROAD_ALT inlays at 4-way intersections and the wall-slab
     * stripes at plot perimeters.
     */
    private void paintRoadRegion(int minX, int maxX, int minZ, int maxZ) {
        World w = ((BukkitWorld) world).getWorld();
        int h = getGroundHeight();
        int topY = w.getMaxHeight();
        int minY = w.getMinHeight();

        int pathSize = getPathWidth();
        int plotSize = getPlotSize();
        double size = plotSize + pathSize;
        double n1, n2, n3;
        int mod1 = 1, mod2 = 0;
        if (pathSize % 2 == 1) {
            n1 = Math.ceil(pathSize / 2.0) - 2;
            n2 = Math.ceil(pathSize / 2.0) - 1;
            n3 = Math.ceil(pathSize / 2.0);
            mod2 = -1;
        } else {
            n1 = Math.floor(pathSize / 2.0) - 2;
            n2 = Math.floor(pathSize / 2.0) - 1;
            n3 = Math.floor(pathSize / 2.0);
        }

        BlockData wall      = MaterialParser.parseBlockData(wgc.getString(DefaultWorldConfigPath.UNCLAIMED_WALL.key(),   "44:7"));
        BlockData floorMain = MaterialParser.parseBlockData(wgc.getString(DefaultWorldConfigPath.ROAD_MAIN_BLOCK.key(),  "5"));
        BlockData floorAlt  = MaterialParser.parseBlockData(wgc.getString(DefaultWorldConfigPath.ROAD_ALT_BLOCK.key(),   "5:2"));
        BlockData plotFloor = MaterialParser.parseBlockData(wgc.getString(DefaultWorldConfigPath.PLOT_FLOOR_BLOCK.key(), "2"));
        BlockData fill      = MaterialParser.parseBlockData(wgc.getString(DefaultWorldConfigPath.FILL_BLOCK.key(),       "3"));

        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                // y == h: floor pattern (main / alt / plotFloor inlays)
                w.getBlockAt(x, h, z).setBlockData(floorBlockFor(x, z, size, n1, n2, n3, mod1, mod2, floorMain, floorAlt, plotFloor, fill), false);
                // y == h+1: wall pattern (slabs on perimeter stripes, AIR elsewhere)
                BlockData layer1 = wallBlockFor(x, z, size, n2, n3, mod1, mod2, wall);
                if (layer1 != null) {
                    w.getBlockAt(x, h + 1, z).setBlockData(layer1, false);
                } else {
                    w.getBlockAt(x, h + 1, z).setType(Material.AIR, false);
                }
                // y > h+1: AIR
                for (int y = h + 2; y < topY; y++) {
                    w.getBlockAt(x, y, z).setType(Material.AIR, false);
                }
                // y < h: FILL_BLOCK (skip bedrock at minY)
                for (int y = h - 1; y > minY; y--) {
                    w.getBlockAt(x, y, z).setBlockData(fill, false);
                }
            }
        }
    }

    private static BlockData floorBlockFor(int valx, int valz, double size,
                                            double n1, double n2, double n3, int mod1, int mod2,
                                            BlockData floorMain, BlockData floorAlt, BlockData plotFloor, BlockData filling) {
        if ((valx - n3 + mod1) % size == 0 || (valx + n3 + mod2) % size == 0) {
            boolean found = false;
            for (double i = n2; i >= 0; i--) {
                if ((valz - i + mod1) % size == 0 || (valz + i + mod2) % size == 0) {
                    found = true; break;
                }
            }
            return found ? floorMain : filling;
        }
        if ((valx - n2 + mod1) % size == 0 || (valx + n2 + mod2) % size == 0) {
            if ((valz - n3 + mod1) % size == 0 || (valz + n3 + mod2) % size == 0
                    || (valz - n2 + mod1) % size == 0 || (valz + n2 + mod2) % size == 0) {
                return floorMain;
            }
            return floorAlt;
        }
        if ((valx - n1 + mod1) % size == 0 || (valx + n1 + mod2) % size == 0) {
            if ((valz - n2 + mod1) % size == 0 || (valz + n2 + mod2) % size == 0
                    || (valz - n1 + mod1) % size == 0 || (valz + n1 + mod2) % size == 0) {
                return floorAlt;
            }
            return floorMain;
        }
        boolean foundZ1 = false;
        for (double i = n1; i >= 0; i--) {
            if ((valz - i + mod1) % size == 0 || (valz + i + mod2) % size == 0) {
                foundZ1 = true; break;
            }
        }
        if (foundZ1) return floorMain;
        if ((valz - n2 + mod1) % size == 0 || (valz + n2 + mod2) % size == 0) {
            return floorAlt;
        }
        boolean foundX3 = false;
        for (double i = n3; i >= 0; i--) {
            if ((valx - i + mod1) % size == 0 || (valx + i + mod2) % size == 0) {
                foundX3 = true; break;
            }
        }
        return foundX3 ? floorMain : plotFloor;
    }

    private static BlockData wallBlockFor(int valx, int valz, double size,
                                           double n2, double n3, int mod1, int mod2,
                                           BlockData wall) {
        if ((valx - n3 + mod1) % size == 0 || (valx + n3 + mod2) % size == 0) {
            boolean found = false;
            for (double i = n2; i >= 0; i--) {
                if ((valz - i + mod1) % size == 0 || (valz + i + mod2) % size == 0) {
                    found = true; break;
                }
            }
            return found ? null : wall;
        }
        boolean foundX = false;
        for (double i = n2; i >= 0; i--) {
            if ((valx - i + mod1) % size == 0 || (valx + i + mod2) % size == 0) {
                foundX = true; break;
            }
        }
        if (!foundX && ((valz - n3 + mod1) % size == 0 || (valz + n3 + mod2) % size == 0)) {
            return wall;
        }
        return null;
    }

    @Override
    public void setOwnerDisplay(PlotId id, String line1, String line2, String line3, String line4) {
        Vector bottom = getPlotBottomLoc(id);
        // Sign block sits one column west and two blocks north of the plot's NW
        // corner; the attached wall slab is on its SOUTH side, so the sign's
        // readable face points NORTH (away from the plot, into the road).
        Vector subtract = bottom.add(-1, getGroundHeight() + 1, -2);
        // Owner-sign palette, mapped to the actual content placed on each line
        // by PlotMeCoreManager.setSingleOwnerSign / refreshClusterOwnerSign:
        //   line1 = "ID: x;z"          -> GOLD + BOLD (header / plot id)
        //   line2 = ""                  -> empty (defensive blank)
        //   line3 = owner name          -> AQUA
        //   line4 = "" or cluster ids   -> GRAY + ITALIC (status / cluster list)
        Component[] lines = new Component[] {
                styled(line1, NamedTextColor.GOLD, TextDecoration.BOLD),
                styled(line2, NamedTextColor.YELLOW),
                styled(line3, NamedTextColor.AQUA),
                styled(line4, NamedTextColor.GRAY, TextDecoration.ITALIC)
        };
        placeSign(subtract, org.bukkit.block.BlockFace.NORTH, lines);
    }

    @Override
    public void setSellerDisplay(PlotId id, String line1, String line2, String line3, String line4) {
        removeSellerDisplay(id);
        Location pillar = new Location(world, bottomX(id) - 1, getGroundHeight() + 1, bottomZ(id) - 1);
        Vector signPos = pillar.add(-1, 0, 0).getVector();
        // For-sale palette is intentionally contrasting from the owner sign so
        // a player can tell at a glance whether a plot is claimed or listed:
        //   line1 = "FOR SALE" header    -> GREEN + BOLD
        //   line2 = price                -> YELLOW
        //   line3 = "" (spacer)           -> empty
        //   line4 = "/plotme buy" hint    -> GRAY + ITALIC
        Component[] lines = new Component[] {
                styled(line1, NamedTextColor.GREEN, TextDecoration.BOLD),
                styled(line2, NamedTextColor.YELLOW),
                styled(line3, NamedTextColor.GRAY),
                styled(line4, NamedTextColor.GRAY, TextDecoration.ITALIC)
        };
        placeSign(signPos, org.bukkit.block.BlockFace.WEST, lines);
    }

    /**
     * Build a styled sign-line component. Returns {@link Component#empty()}
     * for null / blank input so blank lines render as a true empty component
     * instead of an empty-but-styled placeholder (which Paper would still
     * serialise with the colour code attached, polluting the sign NBT).
     */
    private static Component styled(String text, NamedTextColor color, TextDecoration... decorations) {
        if (text == null || text.isEmpty()) {
            return Component.empty();
        }
        Style.Builder style = Style.style().color(color);
        for (TextDecoration deco : decorations) {
            style.decorate(deco);
        }
        return Component.text(text).style(style.build());
    }

    @Override
    public void removeOwnerDisplay(PlotId id) {
        Vector bottom = getPlotBottomLoc(id);
        Vector subtract = bottom.add(-1, getGroundHeight() + 1, -2);
        world.getBlockAt(subtract).setType(Material.AIR, false);
    }

    @Override
    public void removeSellerDisplay(PlotId id) {
        Vector bottom = getPlotBottomLoc(id);
        Location pillar = new Location(world, bottom.getX() - 1, getGroundHeight() + 1, bottom.getZ() - 1);
        IBlock bsign = pillar.add(-1, 0, 0).getBlock();
        bsign.setType(Material.AIR, false);
    }

    /**
     * Place a wall sign at {@code at}, facing {@code facing}, and write up to
     * four Adventure {@link Component} lines to its front face. Callers
     * control styling — this method only does block placement and line
     * assignment. Missing entries in {@code lines} (length &lt; 4 or null
     * elements) render as {@link Component#empty()} so the resulting sign NBT
     * stays clean.
     */
    private void placeSign(Vector at, org.bukkit.block.BlockFace facing, Component[] lines) {
        IBlock b = world.getBlockAt(at);
        b.setType(Material.AIR, false);
        b.setType(Material.OAK_WALL_SIGN, false);
        BlockData data = b.getBlockData();
        if (data instanceof WallSign ws) {
            ws.setFacing(facing);
            b.setBlockData(ws, false);
        }
        if (b.getState() instanceof Sign sign) {
            // Sign#setLine(int, String) is deprecated and slated for removal.
            // Use the Adventure-based SignSide API (Paper 1.20+).
            var side = sign.getSide(Side.FRONT);
            for (int i = 0; i < 4; i++) {
                Component line = (lines != null && i < lines.length && lines[i] != null)
                        ? lines[i]
                        : Component.empty();
                side.line(i, line);
            }
            sign.update(true);
        }
    }

    @Override
    public Vector getPlotBottomLoc(PlotId id) {
        int px = id.x();
        int pz = id.z();
        int pathWidth = getPathWidth();
        int x = px * (getPlotSize() + pathWidth) - getPlotSize() - (int) Math.floor(pathWidth / 2.0);
        int z = pz * (getPlotSize() + pathWidth) - getPlotSize() - (int) Math.floor(pathWidth / 2.0);
        return new Vector(x, 0, z);
    }

    @Override
    public Vector getPlotTopLoc(PlotId id) {
        int px = id.x();
        int pz = id.z();
        int pathWidth = getPathWidth();
        int x = px * (getPlotSize() + pathWidth) - (int) Math.floor(pathWidth / 2.0) - 1;
        int z = pz * (getPlotSize() + pathWidth) - (int) Math.floor(pathWidth / 2.0) - 1;
        World w = ((BukkitWorld) world).getWorld();
        return new Vector(x, w.getMaxHeight(), z);
    }

    @Override
    public void clear(Vector bottom, Vector top, PlotId plotId, ClearEntry entry) {
        clearEntities(bottom, top);
        World w = ((BukkitWorld) world).getWorld();
        int minY = w.getMinHeight();

        BlockData fillBlock = MaterialParser.parseBlockData(
                wgc.getString(DefaultWorldConfigPath.FILL_BLOCK.key(), "3"));
        BlockData plotFloorBlock = MaterialParser.parseBlockData(
                wgc.getString(DefaultWorldConfigPath.PLOT_FLOOR_BLOCK.key(), "2"));

        Set<ChunkCoords> chunks = new HashSet<>();
        for (int x = bottom.getBlockX(); x <= top.getBlockX(); ++x) {
            for (int z = bottom.getBlockZ(); z <= top.getBlockZ(); ++z) {
                chunks.add(new ChunkCoords(x >> 4, z >> 4));
            }
        }
        for (ChunkCoords chunk : chunks) {
            Vector min = new Vector(chunk.x() << 4, minY, chunk.z() << 4);
            entry.chunkqueue.add(new ChunkEntry(chunk, entry, min,
                    fillBlock, plotFloorBlock, getGroundHeight()));
        }
    }

    @Override
    public void adjustPlotFor(Plot plot, boolean claimed, boolean protect, boolean forSale) {
        List<String> wallIds = new ArrayList<>();
        int roadHeight = getGroundHeight();

        String claimedId       = wgc.getString(DefaultWorldConfigPath.WALL_BLOCK.key(),         "44");
        String wallId          = wgc.getString(DefaultWorldConfigPath.UNCLAIMED_WALL.key(),     "44:7");
        String protectedWallId = wgc.getString(DefaultWorldConfigPath.PROTECTED_WALL_BLOCK.key(),"44:4");
        String forsaleWallId   = wgc.getString(DefaultWorldConfigPath.FOR_SALE_WALL_BLOCK.key(), "44:1");

        if (protect)                                       wallIds.add(protectedWallId);
        if (forSale && !wallIds.contains(forsaleWallId))   wallIds.add(forsaleWallId);
        if (claimed && !wallIds.contains(claimedId))       wallIds.add(claimedId);
        if (wallIds.isEmpty())                              wallIds.add(wallId);

        Vector bottom = getPlotBottomLoc(plot.getId());
        Vector top    = getPlotTopLoc(plot.getId());

        // Which of the four edges of THIS plot are internal to a merged
        // cluster? If the plot is merged with its N/S/E/W neighbour, the
        // shared wall strip was already cleared by fillRoad and re-painting
        // it here puts the old slabs back into the middle of the merged
        // area — the bug the /plotme protect toggle was hitting. Skip those
        // sides; the cluster's outer perimeter still gets painted because
        // every other plot also skips its own internal edges and the outer
        // edges have no merged neighbour to mark them internal.
        PlotId pid = plot.getId();
        boolean skipNorth = plot.isMergedWith(new PlotId(pid.x(),     pid.z() - 1));
        boolean skipSouth = plot.isMergedWith(new PlotId(pid.x(),     pid.z() + 1));
        boolean skipEast  = plot.isMergedWith(new PlotId(pid.x() + 1, pid.z()));
        boolean skipWest  = plot.isMergedWith(new PlotId(pid.x() - 1, pid.z()));

        int ctr = 0;
        // North edge (z = bottom.z - 1).
        for (int x = bottom.getBlockX() - 1; x < top.getBlockX() + 1; x++) {
            ctr = (ctr == wallIds.size() - 1) ? 0 : ctr + 1;
            if (skipNorth) continue;
            setWall(world.getBlockAt(x, roadHeight + 1, bottom.getBlockZ() - 1), wallIds.get(ctr));
        }
        // East edge (x = top.x + 1).
        for (int z = bottom.getBlockZ() - 1; z < top.getBlockZ() + 1; z++) {
            ctr = (ctr == wallIds.size() - 1) ? 0 : ctr + 1;
            if (skipEast) continue;
            setWall(world.getBlockAt(top.getBlockX() + 1, roadHeight + 1, z), wallIds.get(ctr));
        }
        // South edge (z = top.z + 1).
        for (int x = top.getBlockX() + 1; x > bottom.getBlockX() - 1; x--) {
            String currentBlockId = wallIds.get(ctr);
            ctr = (ctr == wallIds.size() - 1) ? 0 : ctr + 1;
            if (skipSouth) continue;
            setWall(world.getBlockAt(x, roadHeight + 1, top.getBlockZ() + 1), currentBlockId);
        }
        // West edge (x = bottom.x - 1).
        for (int z = top.getBlockZ() + 1; z > bottom.getBlockZ() - 1; z--) {
            ctr = (ctr == wallIds.size() - 1) ? 0 : ctr + 1;
            if (skipWest) continue;
            setWall(world.getBlockAt(bottom.getBlockX() - 1, roadHeight + 1, z), wallIds.get(ctr));
        }
    }

    private void setWall(IBlock block, String spec) {
        block.setBlockData(MaterialParser.parseBlockData(spec), false);
    }

    @Override
    public Location getPlotHome(PlotId id) {
        Vector bottom = getPlotBottomLoc(id);
        Vector top    = getPlotTopLoc(id);
        return new Location(world,
                bottom.getX() + ((top.getX() - bottom.getX()) / 2),
                getGroundHeight() + 2,
                bottom.getZ() - 2);
    }

    @Override
    public Vector getPlotMiddle(PlotId id) {
        Vector bottom = getPlotBottomLoc(id);
        Vector top    = getPlotTopLoc(id);
        double x = (top.getX() + bottom.getX() + 1) / 2.0;
        double y = getGroundHeight() + 1;
        double z = (top.getZ() + bottom.getZ() + 1) / 2.0;
        return new Vector(x, y, z);
    }
}
