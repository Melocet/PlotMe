package com.worldcretornica.plotme.defaultgenerator;

import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.generator.BlockPopulator;
import org.bukkit.generator.LimitedRegion;
import org.bukkit.generator.WorldInfo;

import java.util.Random;

public class DefaultRoadPopulator extends BlockPopulator {

    private final ConfigurationSection wgc;
    private final int plotSize;
    private final int pathSize;
    private final int roadHeight;

    public DefaultRoadPopulator(ConfigurationSection wgc, int plotSize, int pathSize, int roadHeight) {
        this.wgc = wgc;
        this.plotSize = plotSize;
        this.pathSize = pathSize;
        this.roadHeight = roadHeight;
    }

    @Override
    public void populate(WorldInfo worldInfo, Random rand, int chunkX, int chunkZ, LimitedRegion region) {
        BlockData wall      = MaterialParser.parseBlockData(wgc.getString(DefaultWorldConfigPath.UNCLAIMED_WALL.key(),  "44:7"));
        BlockData floorMain = MaterialParser.parseBlockData(wgc.getString(DefaultWorldConfigPath.ROAD_MAIN_BLOCK.key(), "5"));
        BlockData floorAlt  = MaterialParser.parseBlockData(wgc.getString(DefaultWorldConfigPath.ROAD_ALT_BLOCK.key(),  "5:2"));

        int xx = chunkX << 4;
        int zz = chunkZ << 4;

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

        for (int x = xx; x < xx + 16; x++) {
            for (int z = zz; z < zz + 16; z++) {
                if ((x - n3 + mod1) % size == 0 || (x + n3 + mod2) % size == 0) {
                    boolean found = false;
                    for (double i = n2; i >= 0; i--) {
                        if ((z - i + mod1) % size == 0 || (z + i + mod2) % size == 0) { found = true; break; }
                    }
                    if (found) {
                        set(region,x, roadHeight, z, floorMain);
                    } else {
                        set(region,x, roadHeight, z, floorMain);
                        set(region,x, roadHeight + 1, z, wall);
                    }
                } else {
                    boolean found5 = false;
                    for (double i = n2; i >= 0; i--) {
                        if ((x - i + mod1) % size == 0 || (x + i + mod2) % size == 0) { found5 = true; break; }
                    }
                    if (!found5 && ((z - n3 + mod1) % size == 0 || (z + n3 + mod2) % size == 0)) {
                        set(region,x, roadHeight, z, floorMain);
                        set(region,x, roadHeight + 1, z, wall);
                    }

                    if ((x - n2 + mod1) % size == 0 || (x + n2 + mod2) % size == 0) {
                        if ((z - n3 + mod1) % size == 0 || (z + n3 + mod2) % size == 0
                                || (z - n2 + mod1) % size == 0 || (z + n2 + mod2) % size == 0) {
                            set(region,x, roadHeight, z, floorMain);
                        } else {
                            set(region,x, roadHeight, z, floorAlt);
                        }
                    } else if ((x - n1 + mod1) % size == 0 || (x + n1 + mod2) % size == 0) {
                        if ((z - n2 + mod1) % size == 0 || (z + n2 + mod2) % size == 0
                                || (z - n1 + mod1) % size == 0 || (z + n1 + mod2) % size == 0) {
                            set(region,x, roadHeight, z, floorAlt);
                        } else {
                            set(region,x, roadHeight, z, floorMain);
                        }
                    } else {
                        boolean found = false;
                        for (double i = n1; i >= 0; i--) {
                            if ((z - i + mod1) % size == 0 || (z + i + mod2) % size == 0) { found = true; break; }
                        }
                        if (found) {
                            set(region,x, roadHeight, z, floorMain);
                        } else if ((z - n2 + mod1) % size == 0 || (z + n2 + mod2) % size == 0) {
                            set(region,x, roadHeight, z, floorAlt);
                        } else {
                            boolean found3 = false;
                            for (double i = n3; i >= 0; i--) {
                                if ((x - i + mod1) % size == 0 || (x + i + mod2) % size == 0) { found3 = true; break; }
                            }
                            if (found3) {
                                set(region,x, roadHeight, z, floorMain);
                            }
                        }
                    }
                }
            }
        }
    }

    private void set(LimitedRegion region, int x, int y, int z, BlockData data) {
        if (region.isInRegion(x, y, z)) {
            region.setBlockData(x, y, z, data);
        }
    }
}
