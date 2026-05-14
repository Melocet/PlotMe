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
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Sign;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.WallSign;
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

        BlockData wall = MaterialParser.parseBlockData(wgc.getString(DefaultWorldConfigPath.UNCLAIMED_WALL.key(), "44:7"));
        BlockData fill = MaterialParser.parseBlockData(wgc.getString(DefaultWorldConfigPath.FILL_BLOCK.key(),     "3"));

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
        BlockData fill = MaterialParser.parseBlockData(
                wgc.getString(DefaultWorldConfigPath.PLOT_FLOOR_BLOCK.key(), "2"));

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
                    } else {
                        w.getBlockAt(x, y, z).setBlockData(fill, false);
                    }
                }
            }
        }
    }

    @Override
    public void setOwnerDisplay(PlotId id, String line1, String line2, String line3, String line4) {
        Vector bottom = getPlotBottomLoc(id);
        // Sign block sits one column west and two blocks north of the plot's NW
        // corner; the attached wall slab is on its SOUTH side, so the sign's
        // readable face points NORTH (away from the plot, into the road).
        Vector subtract = bottom.add(-1, getGroundHeight() + 1, -2);
        placeSign(subtract, org.bukkit.block.BlockFace.NORTH, line1, line2, line3, line4);
    }

    @Override
    public void setSellerDisplay(PlotId id, String line1, String line2, String line3, String line4) {
        removeSellerDisplay(id);
        Location pillar = new Location(world, bottomX(id) - 1, getGroundHeight() + 1, bottomZ(id) - 1);
        Vector signPos = pillar.add(-1, 0, 0).getVector();
        placeSign(signPos, org.bukkit.block.BlockFace.WEST, line1, line2, line3, line4);
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

    private void placeSign(Vector at, org.bukkit.block.BlockFace facing,
                           String l1, String l2, String l3, String l4) {
        IBlock b = world.getBlockAt(at);
        b.setType(Material.AIR, false);
        b.setType(Material.OAK_WALL_SIGN, false);
        BlockData data = b.getBlockData();
        if (data instanceof WallSign ws) {
            ws.setFacing(facing);
            b.setBlockData(ws, false);
        }
        if (b.getState() instanceof Sign sign) {
            sign.setLine(0, l1);
            sign.setLine(1, l2);
            sign.setLine(2, l3);
            sign.setLine(3, l4);
            sign.update(true);
        }
    }

    @Override
    public Vector getPlotBottomLoc(PlotId id) {
        int px = id.getX();
        int pz = id.getZ();
        int pathWidth = getPathWidth();
        int x = px * (getPlotSize() + pathWidth) - getPlotSize() - (int) Math.floor(pathWidth / 2.0);
        int z = pz * (getPlotSize() + pathWidth) - getPlotSize() - (int) Math.floor(pathWidth / 2.0);
        return new Vector(x, 0, z);
    }

    @Override
    public Vector getPlotTopLoc(PlotId id) {
        int px = id.getX();
        int pz = id.getZ();
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
            Vector min = new Vector(chunk.getX() << 4, minY, chunk.getZ() << 4);
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

        int ctr = 0;
        Vector bottom = getPlotBottomLoc(plot.getId());
        Vector top    = getPlotTopLoc(plot.getId());

        for (int x = bottom.getBlockX() - 1; x < top.getBlockX() + 1; x++) {
            ctr = (ctr == wallIds.size() - 1) ? 0 : ctr + 1;
            setWall(world.getBlockAt(x, roadHeight + 1, bottom.getBlockZ() - 1), wallIds.get(ctr));
        }
        for (int z = bottom.getBlockZ() - 1; z < top.getBlockZ() + 1; z++) {
            ctr = (ctr == wallIds.size() - 1) ? 0 : ctr + 1;
            setWall(world.getBlockAt(top.getBlockX() + 1, roadHeight + 1, z), wallIds.get(ctr));
        }
        for (int x = top.getBlockX() + 1; x > bottom.getBlockX() - 1; x--) {
            String currentBlockId = wallIds.get(ctr);
            ctr = (ctr == wallIds.size() - 1) ? 0 : ctr + 1;
            setWall(world.getBlockAt(x, roadHeight + 1, top.getBlockZ() + 1), currentBlockId);
        }
        for (int z = top.getBlockZ() + 1; z > bottom.getBlockZ() - 1; z--) {
            ctr = (ctr == wallIds.size() - 1) ? 0 : ctr + 1;
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
