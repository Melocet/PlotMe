package com.worldcretornica.plotme.defaultgenerator;

import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.generator.BlockPopulator;

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
    public void populate(World world, Random rand, Chunk chunk) {
        BlockData plotFloor = MaterialParser.parseBlockData(wgc.getString(DefaultWorldConfigPath.PLOT_FLOOR_BLOCK.key(), "2"));
        BlockData filling   = MaterialParser.parseBlockData(wgc.getString(DefaultWorldConfigPath.FILL_BLOCK.key(),       "3"));

        int xx = chunk.getX() << 4;
        int zz = chunk.getZ() << 4;

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

                for (int y = world.getMinHeight() + 1; y <= roadHeight; y++) {
                    if (y < roadHeight) {
                        world.getBlockAt(x, y, z).setBlockData(filling, false);
                    } else if (modX && modZ) {
                        world.getBlockAt(x, y, z).setBlockData(plotFloor, false);
                    }
                }
            }
        }
    }
}
