package com.worldcretornica.plotme_core.api;

import org.bukkit.Material;
import org.bukkit.block.Biome;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.BlockData;

public interface IBlock {

    Location getLocation();

    IWorld getWorld();

    int getX();

    int getY();

    int getZ();

    String getBiome();

    void setBiome(Biome plains);

    Material getType();

    void setType(Material material, boolean applyPhysics);

    BlockData getBlockData();

    void setBlockData(BlockData data, boolean applyPhysics);

    BlockState getState();
}
