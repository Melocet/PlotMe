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
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

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
        int valx = posx;
        int valz = posz;

        valx -= Math.ceil(((double) pathSize) / 2);
        valx = valx % size;
        if (valx < 0) valx += size;

        valz -= Math.ceil(((double) pathSize) / 2);
        valz = valz % size;
        if (valz < 0) valz += size;

        int plotSize = getPlotSize();
        if (valx >= plotSize || valz >= plotSize) {
            return null; // standing on the road
        }

        int idx = (int) Math.floor((posx + Math.ceil((double) pathSize / 2)) / (double) size);
        int idz = (int) Math.floor((posz + Math.ceil((double) pathSize / 2)) / (double) size);
        if (posx < 0) idx = (int) Math.ceil((posx + Math.ceil((double) pathSize / 2)) / (double) size);
        if (posz < 0) idz = (int) Math.ceil((posz + Math.ceil((double) pathSize / 2)) / (double) size);

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
    public void setBiome(PlotId id, String biome) {
        Biome target;
        try {
            target = Biome.valueOf(biome.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return;
        }
        Vector bottom = getPlotBottomLoc(id);
        Vector top = getPlotTopLoc(id);
        World w = ((BukkitWorld) world).getWorld();
        for (int x = bottom.getBlockX(); x <= top.getBlockX(); x++) {
            for (int z = bottom.getBlockZ(); z <= top.getBlockZ(); z++) {
                for (int y = w.getMinHeight(); y < w.getMaxHeight(); y++) {
                    w.setBiome(x, y, z, target);
                }
            }
        }
        refreshPlotChunks(id);
    }

    protected void log(String msg) {
        Bukkit.getLogger().info("[PlotMe-Generator] " + msg);
    }
}
