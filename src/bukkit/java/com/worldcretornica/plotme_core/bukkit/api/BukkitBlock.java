package com.worldcretornica.plotme_core.bukkit.api;

import com.worldcretornica.plotme_core.api.IBlock;
import com.worldcretornica.plotme_core.api.IWorld;
import com.worldcretornica.plotme_core.api.Location;
import com.worldcretornica.plotme_core.api.Vector;
import org.bukkit.Material;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.BlockData;

public class BukkitBlock implements IBlock {

    private final Block block;

    public BukkitBlock(Block block) {
        this.block = block;
    }

    public Block getHandle() {
        return block;
    }

    @Override
    public Location getLocation() {
        return new Location(getWorld(), getPosition());
    }

    @Override
    public IWorld getWorld() {
        return new BukkitWorld(block.getWorld());
    }

    @Override
    public int getX() {
        return block.getX();
    }

    @Override
    public int getY() {
        return block.getY();
    }

    @Override
    public int getZ() {
        return block.getZ();
    }

    @Override
    public String getBiome() {
        return block.getBiome().toString();
    }

    @Override
    public void setBiome(Biome biome) {
        block.setBiome(biome);
    }

    @Override
    public Material getType() {
        return block.getType();
    }

    @Override
    public void setType(Material material, boolean applyPhysics) {
        block.setType(material, applyPhysics);
    }

    @Override
    public BlockData getBlockData() {
        return block.getBlockData();
    }

    @Override
    public void setBlockData(BlockData data, boolean applyPhysics) {
        block.setBlockData(data, applyPhysics);
    }

    @Override
    public BlockState getState() {
        return block.getState();
    }

    @Override
    public String toString() {
        return "BukkitBlock{" + block.getType() + " @ " + getX() + "," + getY() + "," + getZ() + "}";
    }

    public Vector getPosition() {
        return new Vector(block.getX(), block.getY(), block.getZ());
    }
}
