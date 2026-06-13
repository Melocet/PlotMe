package com.worldcretornica.plotme_core.utils;

import com.worldcretornica.plotme_core.api.IWorld;
import com.worldcretornica.plotme_core.api.Vector;
import com.worldcretornica.plotme_core.bukkit.api.BukkitWorld;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
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
 *
 * Performance notes:
 *  - The Bukkit {@link Chunk} handle and world Y bounds are resolved once
 *    in the constructor so the hot loop does no extra wrapper allocations
 *    or lookups.
 *  - {@code setBlockData(data, false)} is called with applyPhysics=false to
 *    skip the per-block update cascade (lighting/redstone) that dominates
 *    the cost of a naive {@code world.getBlockAt(...).setBlockData(...)}.
 *  - Block fetches go through {@code chunk.getBlock(rx, y, rz)} which uses
 *    chunk-relative coordinates and avoids the world-level chunk lookup
 *    that {@code world.getBlockAt} does on every call.
 *  - {@link IWorld#refreshChunk} is called exactly once at the end so
 *    clients only see one resend per chunk.
 */
public class ChunkEntry {

    private final ChunkCoords chunkCoords;
    private final IWorld world;
    private final World bukkitWorld;
    private final Chunk chunk;

    private final int chunkMinX;
    private final int chunkMinZ;

    private final int interiorMinX;
    private final int interiorMaxX;
    private final int interiorMinZ;
    private final int interiorMaxZ;

    private final int minY;
    private final int maxY;

    private final BlockData fillBlock;
    private final BlockData plotFloorBlock;
    private final BlockData air;
    private final int roadHeight;

    /** Cached worst-case block count for tick-budget accounting. */
    private final int estimatedBlocks;

    public ChunkEntry(ChunkCoords chunk, ClearEntry entry, Vector min,
                      BlockData fillBlock, BlockData plotFloorBlock, int roadHeight) {
        this.chunkCoords = chunk;
        this.world = entry.getPlot().getWorld();
        this.bukkitWorld = ((BukkitWorld) world).getWorld();
        this.chunk = bukkitWorld.getChunkAt(chunk.x(), chunk.z());

        this.chunkMinX = min.getBlockX();
        this.chunkMinZ = min.getBlockZ();

        Vector bottom = entry.getPlot().getPlotBottomLoc();
        Vector top = entry.getPlot().getPlotTopLoc();
        // Clamp the interior to the bounds of this chunk so we can compute
        // the work size up-front and skip the per-block range check.
        this.interiorMinX = Math.max(bottom.getBlockX(), chunkMinX);
        this.interiorMaxX = Math.min(top.getBlockX(), chunkMinX + 15);
        this.interiorMinZ = Math.max(bottom.getBlockZ(), chunkMinZ);
        this.interiorMaxZ = Math.min(top.getBlockZ(), chunkMinZ + 15);

        this.minY = bukkitWorld.getMinHeight();
        this.maxY = bukkitWorld.getMaxHeight();

        this.fillBlock = fillBlock;
        this.plotFloorBlock = plotFloorBlock;
        this.air = Material.AIR.createBlockData();
        this.roadHeight = roadHeight;

        int width  = Math.max(0, interiorMaxX - interiorMinX + 1);
        int depth  = Math.max(0, interiorMaxZ - interiorMinZ + 1);
        int height = Math.max(0, (maxY - 1) - (minY + 1) + 1);
        this.estimatedBlocks = width * depth * height;
    }

    /**
     * @return rough upper bound on the number of {@code setBlockData}
     *         calls this entry will issue. Used by {@link
     *         com.worldcretornica.plotme_core.PlotMeSpool} to budget how
     *         many entries to drain per tick.
     */
    public int getEstimatedBlocks() {
        return estimatedBlocks;
    }

    public void run() {
        // Nothing of this chunk falls inside the plot interior — possible
        // for plots whose bounding box only just clips the chunk.
        if (interiorMinX > interiorMaxX || interiorMinZ > interiorMaxZ) {
            return;
        }

        // Make sure the chunk is loaded before we touch blocks in it.
        if (!chunk.isLoaded()) {
            chunk.load();
        }

        final int floorY = roadHeight;
        for (int wx = interiorMinX; wx <= interiorMaxX; ++wx) {
            int rx = wx - chunkMinX;
            for (int wz = interiorMinZ; wz <= interiorMaxZ; ++wz) {
                int rz = wz - chunkMinZ;
                // Fill column: below road = fill, at road = floor, above = air.
                for (int y = minY + 1; y < floorY; ++y) {
                    Block block = chunk.getBlock(rx, y, rz);
                    block.setBlockData(fillBlock, false);
                }
                if (floorY > minY && floorY < maxY) {
                    Block block = chunk.getBlock(rx, floorY, rz);
                    block.setBlockData(plotFloorBlock, false);
                }
                for (int y = floorY + 1; y < maxY; ++y) {
                    Block block = chunk.getBlock(rx, y, rz);
                    block.setBlockData(air, false);
                }
            }
        }

        world.refreshChunk(chunkCoords.x(), chunkCoords.z());
    }
}
