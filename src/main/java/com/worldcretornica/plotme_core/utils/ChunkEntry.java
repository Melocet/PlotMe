package com.worldcretornica.plotme_core.utils;

import com.worldcretornica.plotme_core.api.IBlock;
import com.worldcretornica.plotme_core.api.IWorld;
import com.worldcretornica.plotme_core.api.Vector;
import com.worldcretornica.plotme_core.bukkit.api.BukkitWorld;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;

/**
 * One chunk's worth of plot-clear work, scheduled via PlotMeSpool so that
 * large plots don't lag a single tick. Per chunk we reset every block
 * inside the plot's interior to the generator's default pattern (fill
 * below the road, plot floor at road height, air above). Border blocks
 * — walls and neighbour-plot edges — are left untouched.
 *
 * Note: the original implementation called World#regenerateChunk and then
 * restored a snapshot of the border blocks. Paper 1.21 dropped support
 * for regenerateChunk (throws UnsupportedOperationException), so this
 * class now does a direct, bounded reset instead.
 */
public class ChunkEntry {

    private final ChunkCoords chunk;
    private final IWorld world;
    private final Vector min;
    private final Vector bottom;
    private final Vector top;

    private final BlockData fillBlock;
    private final BlockData plotFloorBlock;
    private final int roadHeight;

    public ChunkEntry(ChunkCoords chunk, ClearEntry entry, Vector min,
                      BlockData fillBlock, BlockData plotFloorBlock, int roadHeight) {
        this.chunk = chunk;
        this.world = entry.getPlot().getWorld();
        this.min = min;
        this.bottom = entry.getPlot().getPlotBottomLoc();
        this.top = entry.getPlot().getPlotTopLoc();
        this.fillBlock = fillBlock;
        this.plotFloorBlock = plotFloorBlock;
        this.roadHeight = roadHeight;
    }

    public void run() {
        org.bukkit.World w = ((BukkitWorld) world).getWorld();
        int minY = w.getMinHeight();
        int maxY = w.getMaxHeight();
        BlockData air = Material.AIR.createBlockData();

        int interiorMinX = bottom.getBlockX();
        int interiorMaxX = top.getBlockX();
        int interiorMinZ = bottom.getBlockZ();
        int interiorMaxZ = top.getBlockZ();

        for (int dx = 0; dx < 16; ++dx) {
            int wx = min.getBlockX() + dx;
            if (wx < interiorMinX || wx > interiorMaxX) continue;
            for (int dz = 0; dz < 16; ++dz) {
                int wz = min.getBlockZ() + dz;
                if (wz < interiorMinZ || wz > interiorMaxZ) continue;
                for (int y = minY + 1; y < maxY; ++y) {
                    IBlock blockAt = world.getBlockAt(new Vector(wx, y, wz));
                    if (y > roadHeight) {
                        blockAt.setBlockData(air, false);
                    } else if (y == roadHeight) {
                        blockAt.setBlockData(plotFloorBlock, false);
                    } else {
                        blockAt.setBlockData(fillBlock, false);
                    }
                }
            }
        }

        world.refreshChunk(chunk.getX(), chunk.getZ());
    }
}
