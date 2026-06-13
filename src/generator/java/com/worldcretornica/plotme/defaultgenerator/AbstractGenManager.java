package com.worldcretornica.plotme.defaultgenerator;

import com.worldcretornica.plotme_core.PlotId;
import com.worldcretornica.plotme_core.api.IEntity;
import com.worldcretornica.plotme_core.api.IPlayer;
import com.worldcretornica.plotme_core.api.IPlotMe_GeneratorManager;
import com.worldcretornica.plotme_core.api.IWorld;
import com.worldcretornica.plotme_core.api.Vector;
import com.worldcretornica.plotme_core.bukkit.PlotMe_CorePlugin;
import com.worldcretornica.plotme_core.bukkit.api.BukkitWorld;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Common helpers for plot-generator managers — boilerplate plotsize math,
 * entity clearing, plot-id math, biome change. Inlined from the old
 * com.worldcretornica.plotme_abstractgenerator.BukkitAbstractGenManager
 * since the abstractgenerator project is dead upstream.
 */
public abstract class AbstractGenManager implements IPlotMe_GeneratorManager {

    protected final ConfigurationSection wgc;
    protected final IWorld world;

    protected AbstractGenManager(ConfigurationSection wgc, IWorld world) {
        this.wgc = wgc;
        this.world = world;
    }

    @Override
    public int getPlotSize() {
        return wgc.getInt(DefaultWorldConfigPath.PLOT_SIZE.key(), 32);
    }

    @Override
    public int getGroundHeight() {
        return wgc.getInt(DefaultWorldConfigPath.GROUND_LEVEL.key(), 64);
    }

    protected int getPathWidth() {
        return wgc.getInt(DefaultWorldConfigPath.PATH_WIDTH.key(), 7);
    }

    @Override
    public int bottomX(PlotId id) {
        return getPlotBottomLoc(id).getBlockX();
    }

    @Override
    public int bottomZ(PlotId id) {
        return getPlotBottomLoc(id).getBlockZ();
    }

    @Override
    public int topX(PlotId id) {
        return getPlotTopLoc(id).getBlockX();
    }

    @Override
    public int topZ(PlotId id) {
        return getPlotTopLoc(id).getBlockZ();
    }

    @Override
    public Vector getBottom(PlotId id) {
        return getPlotBottomLoc(id);
    }

    @Override
    public Vector getTop(PlotId id) {
        return getPlotTopLoc(id);
    }

    @Override
    public PlotId getPlotId(IPlayer player) {
        return getPlotId(player.getLocation().getVector());
    }

    /**
     * Translate a world XZ position to a plot id, given the plot+path layout.
     * X/Z that fall on the road return null.
     */
    protected PlotId internalgetPlotId(int pathSize, int size, int posx, int posz) {
        int offset = (int) Math.ceil(((double) pathSize) / 2);

        int valx = posx - offset;
        valx = valx % size;
        if (valx < 0) valx += size;

        int valz = posz - offset;
        valz = valz % size;
        if (valz < 0) valz += size;

        int plotSize = getPlotSize();
        if (valx >= plotSize || valz >= plotSize) {
            return null; // standing on the road
        }

        // Tile index = floor(pos/size). The old formula added ceil(pathSize/2)
        // before the divide, which made posx at the eastern/southern edge of
        // a plot (e.g. X=35 with size=39, offset=4) round into the next tile
        // and report the wrong PlotId. floorDiv handles negatives correctly.
        int idx = Math.floorDiv(posx, size);
        int idz = Math.floorDiv(posz, size);

        return new PlotId(idx + 1, idz + 1);
    }

    @Override
    public List<IPlayer> getPlayersInPlot(PlotId id) {
        List<IPlayer> out = new ArrayList<>();
        World w = ((BukkitWorld) world).getWorld();
        for (Player p : w.getPlayers()) {
            PlotId pid = getPlotId(new Vector(p.getLocation().getX(), p.getLocation().getY(), p.getLocation().getZ()));
            if (pid != null && pid.equals(id)) {
                out.add(PlotMe_CorePlugin.getInstance().getAPI().getServerBridge().getPlayer(p.getUniqueId()));
            }
        }
        return out;
    }

    @Override
    public void clearEntities(Vector bottom, Vector top) {
        for (IEntity entity : world.getEntities()) {
            int x = entity.getLocation().getBlockX();
            int z = entity.getLocation().getBlockZ();
            if (x >= bottom.getBlockX() && x <= top.getBlockX()
                    && z >= bottom.getBlockZ() && z <= top.getBlockZ()) {
                if (!(entity instanceof IPlayer)) {
                    entity.remove();
                }
            }
        }
    }

    @Override
    public void refreshPlotChunks(PlotId id) {
        Vector bottom = getPlotBottomLoc(id);
        Vector top = getPlotTopLoc(id);
        int minCX = bottom.getBlockX() >> 4;
        int maxCX = top.getBlockX() >> 4;
        int minCZ = bottom.getBlockZ() >> 4;
        int maxCZ = top.getBlockZ() >> 4;
        for (int cx = minCX; cx <= maxCX; cx++) {
            for (int cz = minCZ; cz <= maxCZ; cz++) {
                world.refreshChunk(cx, cz);
            }
        }
    }

    @Override
    public boolean isBlockInPlot(PlotId id, Vector location) {
        int x = location.getBlockX();
        int z = location.getBlockZ();
        Vector bottom = getPlotBottomLoc(id);
        Vector top = getPlotTopLoc(id);
        return x >= bottom.getBlockX() && x <= top.getBlockX()
                && z >= bottom.getBlockZ() && z <= top.getBlockZ();
    }

    @Override
    public boolean movePlot(PlotId idFrom, PlotId idTo) {
        return false; // optional feature — not implemented in the merged port
    }

    @Override
    public int setBiome(PlotId id, String biome) {
        Vector bottom = getPlotBottomLoc(id);
        Vector top    = getPlotTopLoc(id);
        return setBiomeRegion(world, bottom.getBlockX(), top.getBlockX(),
                              bottom.getBlockZ(), top.getBlockZ(), biome);
    }

    @Override
    public int setBiomeRegion(IWorld targetWorld, int minX, int maxX, int minZ, int maxZ, String biome) {
        Biome target = resolveBiome(biome);
        if (target == null) {
            return 0;
        }
        World w = ((BukkitWorld) targetWorld).getWorld();
        int minY = w.getMinHeight();
        int maxY = w.getMaxHeight() - 1;

        // Minecraft stores biomes per 4x4x4 cell — stepping by 1 hits the same
        // cell 64 times. Step by 4 on every axis, but clamp the final iteration
        // to the inclusive boundary so the upper edge of the plot isn't left
        // on the old biome when (maxX - minX) is not a multiple of 4 (which
        // happens with the default 32-block plot size — 31 wide span, last
        // strip starts at offset 28 and would skip the column at offset 31).
        Set<Long> chunks = new HashSet<>();
        for (int x = minX; x <= maxX; ) {
            for (int z = minZ; z <= maxZ; ) {
                for (int y = minY; y <= maxY; ) {
                    w.setBiome(x, y, z, target);
                    if (y == maxY) break;
                    y = Math.min(y + 4, maxY);
                }
                chunks.add(packChunk(x >> 4, z >> 4));
                if (z == maxZ) break;
                z = Math.min(z + 4, maxZ);
            }
            if (x == maxX) break;
            x = Math.min(x + 4, maxX);
        }

        // Refresh chunks once each (a plot can touch up to ~9x9 = 81 chunks at
        // the default plot size, so the bucket here saves ~80 redundant
        // refreshes vs. a per-column refresh).
        for (long packed : chunks) {
            int cx = (int) (packed >> 32);
            int cz = (int) packed;
            targetWorld.refreshChunk(cx, cz);
        }

        // Block count = full XZ rectangle (the user sees the whole plot turn
        // colour, regardless of how many 4x4x4 cells we wrote). Y-range is
        // implicit since the biome applies to the entire column.
        return (maxX - minX + 1) * (maxZ - minZ + 1);
    }

    private static long packChunk(int cx, int cz) {
        return (((long) cx) << 32) | (cz & 0xFFFFFFFFL);
    }

    /**
     * Resolve a biome name (legacy enum-style "PLAINS", friendly "Birch Forest",
     * or namespaced "minecraft:plains") against the runtime registry.
     */
    @SuppressWarnings("deprecation")
    private static Biome resolveBiome(String input) {
        if (input == null || input.isEmpty()) return null;
        String raw = input.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
        NamespacedKey key;
        if (raw.contains(":")) {
            key = NamespacedKey.fromString(raw);
        } else {
            key = NamespacedKey.minecraft(raw);
        }
        if (key == null) return null;
        Registry<Biome> reg = Bukkit.getRegistry(Biome.class);
        if (reg == null) return null;
        return reg.get(key);
    }

    protected void log(String msg) {
        Bukkit.getLogger().info("[PlotMe-Generator] " + msg);
    }
}
