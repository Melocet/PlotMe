package com.worldcretornica.plotme.defaultgenerator;

import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.generator.BlockPopulator;
import org.bukkit.generator.LimitedRegion;
import org.bukkit.generator.WorldInfo;

import java.util.Random;

public class DefaultContentPopulator extends BlockPopulator {

    private final ConfigurationSection wgc;
    private final int plotSize;
    private final int pathSize;
    private final int roadHeight;

    public DefaultContentPopulator(ConfigurationSection wgc, int plotSize, int pathSize, int roadHeight) {
        this.wgc = wgc;
        this.plotSize = plotSize;
        this.pathSize = pathSize;
        this.roadHeight = roadHeight;
    }

    @Override
    public void populate(WorldInfo worldInfo, Random rand, int chunkX, int chunkZ, LimitedRegion region) {
        BlockData plotFloor = MaterialParser.parseBlockData(wgc.getString(DefaultWorldConfigPath.PLOT_FLOOR_BLOCK.key(), "2"));
        BlockData filling   = MaterialParser.parseBlockData(wgc.getString(DefaultWorldConfigPath.FILL_BLOCK.key(),       "3"));

        int xx = chunkX << 4;
        int zz = chunkZ << 4;

        double size = plotSize + pathSize;

        for (int x = xx; x < xx + 16; x++) {
            int valx = x;
            valx -= Math.ceil(((double) pathSize) / 2);
            valx = valx % (int) size;
            if (valx < 0) valx += size;

            boolean modX = valx < plotSize;

            for (int z = zz; z < zz + 16; z++) {
                int valz = z;
                valz -= Math.ceil(((double) pathSize) / 2);
                valz = valz % (int) size;
                if (valz < 0) valz += size;

                boolean modZ = valz < plotSize;

                for (int y = worldInfo.getMinHeight() + 1; y <= roadHeight; y++) {
                    if (!region.isInRegion(x, y, z)) {
                        continue;
                    }
                    if (y < roadHeight) {
                        region.setBlockData(x, y, z, filling);
                    } else if (modX && modZ) {
                        region.setBlockData(x, y, z, plotFloor);
                    }
                }
            }
        }
    }
}
