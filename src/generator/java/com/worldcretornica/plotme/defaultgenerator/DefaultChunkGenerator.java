package com.worldcretornica.plotme.defaultgenerator;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.generator.BlockPopulator;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * PlotMe road/plot terrain generator, ported from the legacy
 * generateExtBlockSections(short[][]) API to Paper 1.21's
 * generateNoise(WorldInfo, Random, int, int, ChunkData).
 *
 * Layout: identical to the original — bedrock at y=0 of the chunk
 * (relative to the chunk min height), filling up to roadHeight-1,
 * road surface at roadHeight, walls at roadHeight+1. Plot interior
 * blocks (plotFloor) are stamped by DefaultContentPopulator so that
 * vanilla decorations can't overwrite them.
 */
public class DefaultChunkGenerator extends ChunkGenerator {

    private final List<BlockPopulator> blockPopulators = new ArrayList<>(2);
    private final ConfigurationSection wgc;

    private final int plotSize;
    private final int pathSize;
    private final int roadHeight;
    private final BlockData wall;
    private final BlockData floorMain;
    private final BlockData floorAlt;
    private final BlockData plotFloor;
    private final BlockData filling;

    public DefaultChunkGenerator(ConfigurationSection wgc) {
        this.wgc = wgc;
        this.plotSize  = wgc.getInt(DefaultWorldConfigPath.PLOT_SIZE.key(),    32);
        this.pathSize  = wgc.getInt(DefaultWorldConfigPath.PATH_WIDTH.key(),    7);
        this.roadHeight = wgc.getInt(DefaultWorldConfigPath.GROUND_LEVEL.key(), 64);
        this.wall       = MaterialParser.parseBlockData(wgc.getString(DefaultWorldConfigPath.UNCLAIMED_WALL.key(),    "44:7"));
        this.floorMain  = MaterialParser.parseBlockData(wgc.getString(DefaultWorldConfigPath.ROAD_MAIN_BLOCK.key(),   "5"));
        this.floorAlt   = MaterialParser.parseBlockData(wgc.getString(DefaultWorldConfigPath.ROAD_ALT_BLOCK.key(),    "5:2"));
        this.plotFloor  = MaterialParser.parseBlockData(wgc.getString(DefaultWorldConfigPath.PLOT_FLOOR_BLOCK.key(),  "2"));
        this.filling    = MaterialParser.parseBlockData(wgc.getString(DefaultWorldConfigPath.FILL_BLOCK.key(),        "3"));
        blockPopulators.add(new DefaultRoadPopulator(wgc, plotSize, pathSize, roadHeight));
        blockPopulators.add(new DefaultContentPopulator(wgc, plotSize, pathSize, roadHeight));
    }

    @Override public boolean shouldGenerateNoise()       { return false; }
    @Override public boolean shouldGenerateSurface()     { return false; }
    @Override public boolean shouldGenerateCaves()       { return false; }
    @Override public boolean shouldGenerateDecorations() { return false; }
    @Override public boolean shouldGenerateMobs()        { return false; }
    @Override public boolean shouldGenerateStructures()  { return false; }

    @Override
    public List<BlockPopulator> getDefaultPopulators(World world) {
        return blockPopulators;
    }

    @Override
    public void generateNoise(WorldInfo worldInfo, Random random, int cx, int cz, ChunkData chunkData) {
        double size = plotSize + pathSize;
        double n1, n2, n3;
        int mod2 = 0;
        int mod1 = 1;

        if (pathSize % 2 == 1) {
            n1 = Math.ceil((double) pathSize / 2) - 2;
            n2 = Math.ceil((double) pathSize / 2) - 1;
            n3 = Math.ceil((double) pathSize / 2);
            mod2 = -1;
        } else {
            n1 = Math.floor((double) pathSize / 2) - 2;
            n2 = Math.floor((double) pathSize / 2) - 1;
            n3 = Math.floor((double) pathSize / 2);
        }

        int minY  = worldInfo.getMinHeight();
        int top   = roadHeight + 2;
        BlockData bedrock = Material.BEDROCK.createBlockData();

        for (int x = 0; x < 16; x++) {
            int valx = (cx << 4) + x;
            for (int z = 0; z < 16; z++) {
                int valz = (cz << 4) + z;

                chunkData.setBlock(x, minY, z, bedrock);

                for (int y = minY + 1; y < top; y++) {
                    if (y == roadHeight) {
                        if ((valx - n3 + mod1) % size == 0 || (valx + n3 + mod2) % size == 0) {
                            boolean found = false;
                            for (double i = n2; i >= 0; i--) {
                                if ((valz - i + mod1) % size == 0 || (valz + i + mod2) % size == 0) {
                                    found = true; break;
                                }
                            }
                            chunkData.setBlock(x, y, z, found ? floorMain : filling);
                        } else if ((valx - n2 + mod1) % size == 0 || (valx + n2 + mod2) % size == 0) {
                            if ((valz - n3 + mod1) % size == 0 || (valz + n3 + mod2) % size == 0
                                    || (valz - n2 + mod1) % size == 0 || (valz + n2 + mod2) % size == 0) {
                                chunkData.setBlock(x, y, z, floorMain);
                            } else {
                                chunkData.setBlock(x, y, z, floorAlt);
                            }
                        } else if ((valx - n1 + mod1) % size == 0 || (valx + n1 + mod2) % size == 0) {
                            if ((valz - n2 + mod1) % size == 0 || (valz + n2 + mod2) % size == 0
                                    || (valz - n1 + mod1) % size == 0 || (valz + n1 + mod2) % size == 0) {
                                chunkData.setBlock(x, y, z, floorAlt);
                            } else {
                                chunkData.setBlock(x, y, z, floorMain);
                            }
                        } else {
                            boolean found = false;
                            for (double i = n1; i >= 0; i--) {
                                if ((valz - i + mod1) % size == 0 || (valz + i + mod2) % size == 0) {
                                    found = true; break;
                                }
                            }
                            if (found) {
                                chunkData.setBlock(x, y, z, floorMain);
                            } else if ((valz - n2 + mod1) % size == 0 || (valz + n2 + mod2) % size == 0) {
                                chunkData.setBlock(x, y, z, floorAlt);
                            } else {
                                boolean found3 = false;
                                for (double i = n3; i >= 0; i--) {
                                    if ((valx - i + mod1) % size == 0 || (valx + i + mod2) % size == 0) {
                                        found3 = true; break;
                                    }
                                }
                                chunkData.setBlock(x, y, z, found3 ? floorMain : plotFloor);
                            }
                        }
                    } else if (y == (roadHeight + 1)) {
                        if ((valx - n3 + mod1) % size == 0 || (valx + n3 + mod2) % size == 0) {
                            boolean found = false;
                            for (double i = n2; i >= 0; i--) {
                                if ((valz - i + mod1) % size == 0 || (valz + i + mod2) % size == 0) {
                                    found = true; break;
                                }
                            }
                            if (!found) chunkData.setBlock(x, y, z, wall);
                        } else {
                            boolean found = false;
                            for (double i = n2; i >= 0; i--) {
                                if ((valx - i + mod1) % size == 0 || (valx + i + mod2) % size == 0) {
                                    found = true; break;
                                }
                            }
                            if (!found && ((valz - n3 + mod1) % size == 0 || (valz + n3 + mod2) % size == 0)) {
                                chunkData.setBlock(x, y, z, wall);
                            }
                        }
                    } else {
                        chunkData.setBlock(x, y, z, filling);
                    }
                }
            }
        }
    }

    @Override
    public org.bukkit.Location getFixedSpawnLocation(World world, Random random) {
        return new org.bukkit.Location(world,
                wgc.getInt(DefaultWorldConfigPath.X_TRANSLATION.key(), 0),
                roadHeight + 2,
                wgc.getInt(DefaultWorldConfigPath.Z_TRANSLATION.key(), 0));
    }
}
