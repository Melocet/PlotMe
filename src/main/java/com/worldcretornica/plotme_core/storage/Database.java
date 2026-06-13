package com.worldcretornica.plotme_core.storage;

import com.worldcretornica.plotme_core.Plot;
import com.worldcretornica.plotme_core.PlotId;
import com.worldcretornica.plotme_core.PlotMeCoreManager;
import com.worldcretornica.plotme_core.PlotMe_Core;
import com.worldcretornica.plotme_core.api.IWorld;
import com.worldcretornica.plotme_core.api.event.PlotLoadEvent;
import com.worldcretornica.plotme_core.api.event.PlotWorldLoadEvent;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.Vector;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public abstract class Database {

    /**
     * Period of the async dirty-plot flusher, in server ticks (20 ticks = 1 second).
     * Default 5 seconds.
     */
    private static final long FLUSH_PERIOD_TICKS = 20L * 5L;

    public final ConcurrentHashMap<IWorld, HashMap<PlotId, Plot>> plots = new ConcurrentHashMap<>();
    final PlotMe_Core plugin;
    public long nextPlotId = 1;
    Connection connection;

    /**
     * Set of plots whose in-memory state has diverged from the database and needs to be
     * persisted on the next flusher tick. Uses a ConcurrentHashMap-backed set so callers
     * can mark plots dirty from any thread (main or async) without external locking.
     */
    private final Set<PlotKey> dirtyPlots = ConcurrentHashMap.newKeySet();

    /**
     * Task id of the recurring async flusher started by {@link #startFlusher()}, or -1
     * when no flusher is running.
     */
    private int flushTaskId = -1;

    /**
     * Guards the actual JDBC flush body so the periodic async flusher and an explicit
     * {@link #flushNow()} call (e.g. from disable/reload) can never run concurrently
     * against the same connection.
     */
    private final Object flushLock = new Object();

    public Database(PlotMe_Core plugin) {
        this.plugin = plugin;
    }

    /**
     * Parse the {@code expiredDate} column into a {@link LocalDate}. New rows are written as
     * ISO {@code yyyy-MM-dd} strings via {@link #writePlotBatched}, but legacy databases stored
     * either a {@code java.sql.Date} or a {@code DATETIME} value (e.g. {@code "2024-06-13 00:00:00"}).
     * This helper accepts both forms so existing servers can upgrade without a migration script:
     * we try a straight {@link LocalDate#parse} first, then fall back to taking the leading
     * {@code yyyy-MM-dd} chunk before parsing again.
     *
     * @param raw the value as returned by {@code ResultSet#getString("expiredDate")}; may be null
     * @return the parsed expiry date, or {@code null} if the column was null or unparseable
     */
    static LocalDate parseExpiredDate(String raw) {
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        try {
            return LocalDate.parse(raw);
        } catch (DateTimeParseException firstAttempt) {
            // Legacy DATETIME rows look like "yyyy-MM-dd HH:mm:ss[.fffffffff]" — strip the
            // time component and try again before giving up.
            if (raw.length() >= 10) {
                try {
                    return LocalDate.parse(raw.substring(0, 10));
                } catch (DateTimeParseException ignored) {
                    // fall through to null
                }
            }
            return null;
        }
    }

    /**
     * Composite key identifying a plot in the dirty set. We can't use {@link Plot} itself
     * because the same plot instance may be referenced through different lookups, and we
     * want dedup across rapid-fire edits to converge on a single write per flush cycle.
     */
    private static final class PlotKey {

        private final IWorld world;
        private final PlotId id;

        PlotKey(IWorld world, PlotId id) {
            this.world = world;
            this.id = id;
        }

        @Override public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof PlotKey)) return false;
            PlotKey other = (PlotKey) o;
            return Objects.equals(world, other.world) && Objects.equals(id, other.id);
        }

        @Override public int hashCode() {
            return Objects.hash(world, id);
        }
    }

    /**
     * Very demanding task depending on how many plots in each world.
     *
     * @return all plots
     */
    public List<Plot> getPlots() {
        Vector<Plot> allPlots = new Vector<>();
        for (HashMap<PlotId, Plot> plotIdPlotHashMap : plots.values()) {
            allPlots.addAll(plotIdPlotHashMap.values());
        }
        return allPlots;
    }

    /**
     * Closes the connecection to the database.
     * This will not close the connection if the connection is null.
     */
    public void closeConnection() {
        if (connection != null) {
            try {
                connection.close();
            } catch (SQLException e) {
                plugin.getLogger().severe("Could not close database connection: ");
                plugin.getLogger().severe(e.getMessage());
            }
        }
    }

    public abstract Connection startConnection();

    /**
     * The database connection
     * @return the connection to the database
     */
    Connection getConnection() {
        try {
            if (connection == null || connection.isClosed()) {
                return startConnection();
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Oh no! A connection error occurred:");
            plugin.getLogger().severe(e.getMessage());
        }
        return connection;
    }

    protected abstract void createTables();

    /**
     * Get the number of plots in the world
     * @param world plotworld to check
     * @return number of plots in the world
     */
    public int getWorldPlotCount(IWorld world) {
        HashMap<PlotId, Plot> worldPlots = plots.get(world);
        return worldPlots == null ? 0 : worldPlots.size();
    }

    /**
     * Get the number of plots in the database
     * @return number of plots in the world
     */
    public int getTotalPlotCount() {
        return plots.size();
    }

    public int getPlotCount(IWorld world, final UUID uuid) {
        HashMap<PlotId, Plot> worldPlots = plots.get(world);
        if (worldPlots == null) return 0;
        return (int) worldPlots.values().stream()
                .filter(plot -> plot.getOwnerId().equals(uuid))
                .count();
    }

    public void addPlot(Plot plot) {
        addPlotToCache(plot);
        savePlot(plot);
    }

    /**
     * Mark a plot as needing persistence on the next async flusher tick. This is the
     * preferred way to schedule a plot write from gameplay event handlers / commands:
     * it never touches JDBC on the calling thread, and repeated calls between flushes
     * collapse into a single write.
     *
     * @param world plot's world
     * @param id    plot id within that world
     */
    public void markDirty(IWorld world, PlotId id) {
        if (world == null || id == null) {
            return;
        }
        dirtyPlots.add(new PlotKey(world, id));
    }

    /**
     * Convenience overload that pulls world/id off the Plot instance.
     */
    public void markDirty(Plot plot) {
        if (plot == null) {
            return;
        }
        markDirty(plot.getWorld(), plot.getId());
    }

    /**
     * Start the recurring async flusher. Should be invoked once from
     * {@link PlotMe_Core#enable()} (or when the {@link Database} is constructed) so
     * dirty plots are drained on a fixed cadence rather than on every gameplay event.
     */
    public void startFlusher() {
        if (flushTaskId != -1) {
            return;
        }
        flushTaskId = plugin.getServerBridge().runTaskTimerAsynchronously(new Runnable() {
            @Override public void run() {
                drainDirtySet();
            }
        }, FLUSH_PERIOD_TICKS, FLUSH_PERIOD_TICKS);
    }

    /**
     * Cancel the recurring async flusher. Safe to call when no flusher is running.
     * Callers that need a final drain should invoke {@link #flushNow()} first; this
     * method does not flush by itself because shutdown order matters (we want the
     * final flush to happen synchronously on the main thread before the connection
     * gets closed).
     */
    public void stopFlusher() {
        if (flushTaskId != -1) {
            plugin.getServerBridge().cancelTask(flushTaskId);
            flushTaskId = -1;
        }
    }

    /**
     * Synchronously drain the dirty-plot set on the calling thread. Used on plugin
     * disable and /plotme reload so we don't lose pending writes when the connection
     * is about to be closed. May block the main thread; callers must accept this.
     */
    public void flushNow() {
        drainDirtySet();
    }

    /**
     * Iterate the dirty set, resolving each key back to a live Plot and batching a
     * write for it. Entries are removed from the set before the write so a concurrent
     * markDirty during the flush is preserved for the next cycle (we never lose a
     * dirty marker, we only sometimes write twice).
     */
    private void drainDirtySet() {
        if (dirtyPlots.isEmpty()) {
            return;
        }
        synchronized (flushLock) {
            for (PlotKey key : dirtyPlots) {
                // Remove first so a concurrent mutation that re-marks the plot
                // between this point and the write survives into the next flush.
                dirtyPlots.remove(key);
                HashMap<PlotId, Plot> worldPlots = plots.get(key.world);
                if (worldPlots == null) {
                    continue;
                }
                Plot plot = worldPlots.get(key.id);
                if (plot == null) {
                    // Plot was deleted between markDirty and the flush; deletePlot
                    // already wrote the removal, so nothing to do here.
                    continue;
                }
                try {
                    writePlotBatched(plot);
                } catch (SQLException e) {
                    plugin.getLogger().severe("Async flush failed for plot " + plot.getInternalID()
                            + " in world " + key.world.getName() + ": " + e.getMessage());
                    plugin.getLogger().severe("Error Code: " + e.getErrorCode());
                    plugin.getLogger().severe("SQLState: " + e.getSQLState());
                }
            }
        }
    }

    private void addPlotToCache(Plot plot) {
        plots.computeIfAbsent(plot.getWorld(), w -> new HashMap<>()).put(plot.getId(), plot);
    }

    public boolean deletePlot(Plot plot) {
        // Drop any pending dirty marker for this plot so the async flusher doesn't
        // race and re-insert the row we're about to delete.
        dirtyPlots.remove(new PlotKey(plot.getWorld(), plot.getId()));
        deletePlotFromStorage(plot);
        return deletePlotFromCache(plot);

    }

    private boolean deletePlotFromCache(Plot plot) {
        HashMap<PlotId, Plot> worldPlots = plots.get(plot.getWorld());
        if (worldPlots != null) worldPlots.remove(plot.getId());
        return true;
    }

    private void deletePlotFromStorage(Plot plot) {
        deleteAllFrom(plot.getInternalID(), "plotmecore_allowed");
        deleteAllFrom(plot.getInternalID(), "plotmecore_denied");
        deleteAllFrom(plot.getInternalID(), "plotmecore_metadata");
        deleteAllFrom(plot.getInternalID(), "plotmecore_likes");
        deleteAllFrom(plot.getInternalID(), "plotmecore_merged");
        deleteAllFrom(plot.getInternalID(), "plotmecore_plots");
    }

    public void deleteAllFrom(final long internalID, final String table) {
        try (Statement statement = getConnection().createStatement()) {
            statement.execute("DELETE FROM " + table + " WHERE plot_id = " + internalID);
            getConnection().commit();
        } catch (SQLException e) {
            plugin.getLogger().severe("Error deleting plot " + internalID + "'s data from table: " + table);
            plugin.getLogger().severe("Details: " + e.getMessage());
            plugin.getLogger().severe("Error Code: " + e.getErrorCode());
            plugin.getLogger().severe("SQLState: " + e.getSQLState());
        }
    }


    /**
     * Placeholder.
     *
     * @param uuid
     * @return plots. unmodifiable.
     */

    public List<Plot> getPlayerPlots(final UUID uuid) {
        ArrayList<Plot> filter = new ArrayList<>();
        for (HashMap<PlotId, Plot> plotIdPlotHashMap : plots.values()) {
            for (Plot plot : plotIdPlotHashMap.values()) {
                if (plot.getOwnerId().equals(uuid)) {
                    filter.add(plot);
                }
            }
        }
        return List.copyOf(filter);
    }

    /**
     * Placeholder.
     *
     * @param world
     * @param uuid
     * @return owned plots. unmodifiable.
     */
    public List<Plot> getOwnedPlots(final IWorld world, final UUID uuid) {
        HashMap<PlotId, Plot> worldPlots = plots.get(world);
        if (worldPlots == null) return List.of();
        return worldPlots.values().stream()
                .filter(plot -> plot.getOwnerId().equals(uuid) && plot.getWorld().equals(world))
                .collect(Collectors.toUnmodifiableList());
    }

    public void loadPlotsAsynchronously(final IWorld world) {
        plugin.getServerBridge().runTaskAsynchronously(new Runnable() {
            @Override
            public void run() {
                plugin.getLogger().info("Loading plots for world " + world.getName());
                HashMap<PlotId, Plot> plots2 = getPlots(world);
                plots.put(world, plots2);
                PlotWorldLoadEvent eventWorld = new PlotWorldLoadEvent(world, plots2.size());
                plugin.getEventBus().post(eventWorld);
                for (Plot plot : plots2.values()) {
                    PlotLoadEvent event = new PlotLoadEvent(plot);
                    plugin.getEventBus().post(event);

                }

            }

            private HashMap<PlotId, Plot> getPlots(IWorld world) {
                HashMap<PlotId, Plot> ret = new HashMap<>();
                Connection connection = getConnection();
                try (PreparedStatement statementPlot = connection.prepareStatement("SELECT * FROM plotmecore_plots WHERE LOWER(world) = ?");
                        PreparedStatement statementAllowed = connection.prepareStatement("SELECT * FROM plotmecore_allowed WHERE plot_id = ?");
                        PreparedStatement statementDenied = connection.prepareStatement("SELECT * FROM plotmecore_denied WHERE plot_id = ?");
                        PreparedStatement statementLikes = connection.prepareStatement("SELECT * FROM plotmecore_likes WHERE plot_id = ?");
                        PreparedStatement statementMetadata = connection.prepareStatement("SELECT * FROM plotmecore_metadata WHERE plot_id = ?");
                        PreparedStatement statementMerged = connection.prepareStatement("SELECT mergedX, mergedZ FROM plotmecore_merged WHERE plot_id = ?")
                ) {
                    statementPlot.setString(1, world.getName().toLowerCase());
                    try (ResultSet setPlots = statementPlot.executeQuery()) {
                        while (setPlots.next()) {
                            long internalID = setPlots.getLong("plot_id");
                            PlotId id = new PlotId(setPlots.getInt("plotX"), setPlots.getInt("plotZ"));
                            String owner = setPlots.getString("owner");
                            UUID ownerId = UUID.fromString(setPlots.getString("ownerID"));
                            String biome = setPlots.getString("biome");
                            LocalDate expiredDate = Database.parseExpiredDate(setPlots.getString("expiredDate"));
                            boolean finished = setPlots.getBoolean("finished");
                            String finishedDate = setPlots.getString("finishedDate");
                            String createdDate = setPlots.getString("createdDate");
                            double price = setPlots.getDouble("price");
                            boolean forSale = setPlots.getBoolean("forSale");
                            boolean protect = setPlots.getBoolean("protected");
                            String plotName = setPlots.getString("plotName");
                            int plotLikes = setPlots.getInt("plotLikes");
                            com.worldcretornica.plotme_core.api.Vector
                                    topLoc = PlotMeCoreManager.getInstance().getPlotTopLoc(world, id);
                            com.worldcretornica.plotme_core.api.Vector bottomLoc =
                                    PlotMeCoreManager.getInstance().getPlotBottomLoc(world, id);
                            HashMap<String, Map<String, String>> metadata = new HashMap<>();
                            HashMap<String, Plot.AccessLevel> allowed = new HashMap<>();
                            HashSet<String> denied = new HashSet<>();
                            HashSet<UUID> likers = new HashSet<>();
                            statementAllowed.setLong(1, internalID);
                            try (ResultSet setAllowed = statementAllowed.executeQuery()) {
                                while (setAllowed.next()) {
                                    allowed.put(setAllowed.getString("player"), Plot.AccessLevel.getAccessLevel(setAllowed.getInt("access")));
                                }
                            }
                            statementDenied.setLong(1, internalID);
                            try (ResultSet setDenied = statementDenied.executeQuery()) {
                                while (setDenied.next()) {
                                    denied.add(setDenied.getString("player"));
                                }
                            }
                            statementLikes.setLong(1, internalID);
                            try (ResultSet setLikes = statementLikes.executeQuery()) {
                                while (setLikes.next()) {
                                    likers.add(UUID.fromString(setLikes.getString("player")));
                                }
                            }

                            statementMetadata.setLong(1, internalID);
                            try (ResultSet setMetadata = statementMetadata.executeQuery()) {
                                while (setMetadata.next()) {
                                    String pluginname = setMetadata.getString("pluginName");
                                    String propertyname = setMetadata.getString("propertyName");
                                    String propertyvalue = setMetadata.getString("propertyValue");
                                    if (!metadata.containsKey(pluginname)) {
                                        metadata.put(pluginname, new HashMap<String, String>());
                                    }
                                    metadata.get(pluginname).put(propertyname, propertyvalue);
                                }
                            }

                            Plot plot =
                                    new Plot(internalID, owner, ownerId, world, biome, expiredDate, allowed, denied,
                                            likers, id, price, forSale, finished, finishedDate, protect, metadata, plotLikes, plotName, topLoc,
                                            bottomLoc, createdDate);
                            // Merged-with links. The table is additive — older databases
                            // simply won't have any rows here yet, so a missing table is
                            // tolerated by swallowing the SQLException (createTables runs
                            // every connect, so the table will exist after one boot).
                            try {
                                statementMerged.setLong(1, internalID);
                                try (ResultSet setMerged = statementMerged.executeQuery()) {
                                    java.util.ArrayList<PlotId> linked = new java.util.ArrayList<>();
                                    while (setMerged.next()) {
                                        linked.add(new PlotId(
                                                setMerged.getInt("mergedX"),
                                                setMerged.getInt("mergedZ")));
                                    }
                                    plot.addMergedWith(linked);
                                }
                            } catch (SQLException mergedEx) {
                                plugin.getLogger().warning("plotmecore_merged not readable for plot "
                                        + internalID + ": " + mergedEx.getMessage());
                            }
                            ret.put(plot.getId(), plot);
                        }
                    }
                } catch (SQLException ex) {
                    plugin.getLogger().severe("Load exception :");
                    plugin.getLogger().severe(ex.getMessage());
                    plugin.getLogger().severe("Details: " + ex.getMessage());
                    plugin.getLogger().severe("Error Code: " + ex.getErrorCode());
                    plugin.getLogger().severe("SQLState: " + ex.getSQLState());
                }
                return ret;
            }
        });
    }

    public Plot getPlot(PlotId id, IWorld world) {
        HashMap<PlotId, Plot> worldPlots = plots.get(world);
        return worldPlots == null ? null : worldPlots.get(id);
    }


    public List<Plot> getExpiredPlots(final IWorld world) {
        HashMap<PlotId, Plot> worldPlots = plots.get(world);
        if (worldPlots == null) return List.of();
        final LocalDate today = LocalDate.now();
        return worldPlots.values().stream()
                .filter(plot -> plot.getExpiredDate() != null
                        && plot.getExpiredDate().isBefore(today)
                        && plot.getWorld().equals(world))
                .collect(Collectors.toUnmodifiableList());
    }

    public List<Plot> getFinishedPlots(final IWorld world) {
        HashMap<PlotId, Plot> worldPlots = plots.get(world);
        if (worldPlots == null) return List.of();
        return worldPlots.values().stream()
                .filter(plot -> plot.isFinished() && plot.getWorld().equals(world))
                .collect(Collectors.toUnmodifiableList());
    }

    public void incrementNextPlotId() {
        this.setNextPlotId(this.nextPlotId + 1);
    }

    public void setNextPlotId(final long id) {
        this.nextPlotId = id;
        try (Statement statement = getConnection().createStatement()) {
            statement.execute("DELETE FROM plotmecore_nextid;");
            statement.execute("INSERT INTO plotmecore_nextid VALUES (" + id + ");");
        } catch (SQLException e) {
            plugin.getLogger().severe("Error setting next internal Plot id. Details below: ");
            plugin.getLogger().severe(e.getMessage());
        }
    }

    /**
     * Schedule a plot to be persisted on the next async flusher tick. Historically this
     * method performed a full DELETE+INSERT across five tables on the calling thread
     * (which on Bukkit means the main server thread) for every single edit -- a deny,
     * a trust, a like, etc. -- causing visible TPS hitches under load.
     *
     * The new contract:
     *  - internalID assignment for brand-new plots still happens synchronously here,
     *    because callers immediately expect getInternalID() to be non-zero after this
     *    returns (e.g. for foreign-key lookups in other tables).
     *  - The actual JDBC write is deferred to the async flusher via {@link #markDirty}.
     *  - Callers who need durability before continuing must call {@link #flushNow()}.
     */
    public void savePlot(Plot plot) {
        if (plot.getInternalID() == 0) {
            plot.setInternalID(nextPlotId);
            incrementNextPlotId();
        }
        markDirty(plot);
    }

    /**
     * Async batched write path. Replaces the per-row execute+commit loops that the old
     * {@link #writePlotToStorage(Plot)} used. For each child table (denied / allowed /
     * likes / metadata) we open one prepared statement, call addBatch() per row, then
     * executeBatch() once. A single commit at the end makes the whole plot write atomic
     * from the database's perspective.
     *
     * Must only be invoked from the async flusher (or from {@link #flushNow()} during
     * shutdown). The synchronized(flushLock) in drainDirtySet guarantees this method
     * sees serialized access to the shared {@link #connection}.
     */
    private void writePlotBatched(final Plot plot) throws SQLException {
        final Connection conn = getConnection();
        if (conn == null) {
            throw new SQLException("No database connection available for batched plot write");
        }
        final long internalID = plot.getInternalID();

        // Clear out the old row + child rows. We still use single-statement deletes here
        // because that's cheaper than diffing against the current DB state for a tiny
        // number of rows per plot, and it preserves the existing semantics of
        // writePlotToStorage (which also did a full replace).
        try (Statement delStmt = conn.createStatement()) {
            delStmt.addBatch("DELETE FROM plotmecore_allowed  WHERE plot_id = " + internalID);
            delStmt.addBatch("DELETE FROM plotmecore_denied   WHERE plot_id = " + internalID);
            delStmt.addBatch("DELETE FROM plotmecore_metadata WHERE plot_id = " + internalID);
            delStmt.addBatch("DELETE FROM plotmecore_likes    WHERE plot_id = " + internalID);
            delStmt.addBatch("DELETE FROM plotmecore_merged   WHERE plot_id = " + internalID);
            delStmt.addBatch("DELETE FROM plotmecore_plots    WHERE plot_id = " + internalID);
            delStmt.executeBatch();
        }

        // Re-insert the plot header row.
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO plotmecore_plots(plot_id,plotX, plotZ, world, ownerID, owner, biome, finished, finishedDate, forSale, price, "
                        + "protected, "
                        + "expiredDate, topX, topZ, bottomX, bottomZ, plotLikes, createdDate) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,"
                        + "?)")) {
            ps.setLong(1, internalID);
            ps.setInt(2, plot.getId().x());
            ps.setInt(3, plot.getId().z());
            ps.setString(4, plot.getWorld().getName().toLowerCase());
            ps.setString(5, plot.getOwnerId().toString());
            ps.setString(6, plot.getOwner());
            ps.setString(7, plot.getBiome());
            ps.setBoolean(8, plot.isFinished());
            ps.setString(9, plot.getFinishedDate());
            ps.setBoolean(10, plot.isForSale());
            ps.setDouble(11, plot.getPrice());
            ps.setBoolean(12, plot.isProtected());
            // Persist expiry as an ISO yyyy-MM-dd string. This works for both the legacy
            // DATETIME column (drivers accept "yyyy-MM-dd" as midnight) and a future DATE
            // column, so existing servers don't need a schema migration to upgrade.
            if (plot.getExpiredDate() == null) {
                ps.setNull(13, Types.VARCHAR);
            } else {
                ps.setString(13, plot.getExpiredDate().toString());
            }
            ps.setInt(14, plot.getTopX());
            ps.setInt(15, plot.getTopZ());
            ps.setInt(16, plot.getBottomX());
            ps.setInt(17, plot.getBottomZ());
            ps.setInt(18, plot.getLikes());
            ps.setString(19, plot.getCreatedDate());
            ps.executeUpdate();
        }

        // Denied players -- batched.
        if (!plot.getDenied().isEmpty()) {
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO plotmecore_denied (plot_id, player) VALUES(?,?)")) {
                for (String denied : plot.getDenied()) {
                    ps.setLong(1, internalID);
                    ps.setString(2, denied);
                    ps.addBatch();
                }
                ps.executeBatch();
            }
        }

        // Allowed / trusted members -- batched.
        if (!plot.getMembers().isEmpty()) {
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO plotmecore_allowed (plot_id, player, access) VALUES(?,?, ?)")) {
                for (Map.Entry<String, Plot.AccessLevel> member : plot.getMembers().entrySet()) {
                    ps.setLong(1, internalID);
                    ps.setString(2, member.getKey());
                    ps.setInt(3, member.getValue().getLevel());
                    ps.addBatch();
                }
                ps.executeBatch();
            }
        }

        // Likers -- batched.
        if (!plot.getLikers().isEmpty()) {
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO plotmecore_likes (plot_id, player) VALUES(?, ?)")) {
                for (UUID player : plot.getLikers()) {
                    ps.setLong(1, internalID);
                    ps.setString(2, player.toString());
                    ps.addBatch();
                }
                ps.executeBatch();
            }
        }

        // Merged-with links -- batched. Each row pairs (plot_id, mergedX,
        // mergedZ). The link is duplicated on both sides at the in-memory
        // layer, so loading each plot's set independently is consistent
        // without extra joins.
        if (!plot.getMergedWith().isEmpty()) {
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO plotmecore_merged (plot_id, mergedX, mergedZ) VALUES (?,?,?)")) {
                for (PlotId merged : plot.getMergedWith()) {
                    ps.setLong(1, internalID);
                    ps.setInt(2, merged.x());
                    ps.setInt(3, merged.z());
                    ps.addBatch();
                }
                ps.executeBatch();
            } catch (SQLException mergedEx) {
                // Don't blow up the whole plot write if the table is missing
                // on a server that's mid-upgrade -- just log and continue.
                plugin.getLogger().warning("plotmecore_merged write failed for plot "
                        + internalID + ": " + mergedEx.getMessage());
            }
        }

        // Per-plugin metadata -- batched across all (plugin, key, value) triples.
        if (!plot.getAllPlotProperties().isEmpty()) {
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO plotmecore_metadata(plot_id, pluginName, propertyName, "
                            + "propertyValue) VALUES (?,?,?,?)")) {
                boolean any = false;
                for (Map.Entry<String, Map<String, String>> metadata : plot.getAllPlotProperties().entrySet()) {
                    for (Map.Entry<String, String> stringStringEntry : metadata.getValue().entrySet()) {
                        ps.setLong(1, internalID);
                        ps.setString(2, metadata.getKey());
                        ps.setString(3, stringStringEntry.getKey());
                        ps.setString(4, stringStringEntry.getValue());
                        ps.addBatch();
                        any = true;
                    }
                }
                if (any) {
                    ps.executeBatch();
                }
            }
        }

        // One commit per plot makes the whole replace atomic.
        conn.commit();
    }

    /**
     * Legacy synchronous write path. Retained because it's still the simplest way to
     * persist a plot during shutdown if anything bypasses the dirty-set machinery, but
     * gameplay paths should use {@link #markDirty} / {@link #savePlot} instead.
     */
    @SuppressWarnings("unused")
    private void writePlotToStorage(final Plot plot) {
        //first delete the plot (if exists) from the database
        deletePlotFromStorage(plot);
        try (PreparedStatement ps = getConnection().prepareStatement(
                "INSERT INTO plotmecore_plots(plot_id,plotX, plotZ, world, ownerID, owner, biome, finished, finishedDate, forSale, price, "
                        + "protected, "
                        + "expiredDate, topX, topZ, bottomX, bottomZ, plotLikes, createdDate) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,"
                        + "?)")) {
            ps.setLong(1, plot.getInternalID());
            ps.setInt(2, plot.getId().x());
            ps.setInt(3, plot.getId().z());
            ps.setString(4, plot.getWorld().getName().toLowerCase());
            ps.setString(5, plot.getOwnerId().toString());
            ps.setString(6, plot.getOwner());
            ps.setString(7, plot.getBiome());
            ps.setBoolean(8, plot.isFinished());
            ps.setString(9, plot.getFinishedDate());
            ps.setBoolean(10, plot.isForSale());
            ps.setDouble(11, plot.getPrice());
            ps.setBoolean(12, plot.isProtected());
            // Persist expiry as an ISO yyyy-MM-dd string. This works for both the legacy
            // DATETIME column (drivers accept "yyyy-MM-dd" as midnight) and a future DATE
            // column, so existing servers don't need a schema migration to upgrade.
            if (plot.getExpiredDate() == null) {
                ps.setNull(13, Types.VARCHAR);
            } else {
                ps.setString(13, plot.getExpiredDate().toString());
            }
            ps.setInt(14, plot.getTopX());
            ps.setInt(15, plot.getTopZ());
            ps.setInt(16, plot.getBottomX());
            ps.setInt(17, plot.getBottomZ());
            ps.setInt(18, plot.getLikes());
            ps.setString(19, plot.getCreatedDate());
            ps.executeUpdate();
            getConnection().commit();
        } catch (SQLException e) {
            plugin.getLogger().severe("Insert Exception :");
            plugin.getLogger().severe(e.getMessage());
            plugin.getLogger().severe("Details: " + e.getMessage());
            plugin.getLogger().severe("Error Code: " + e.getErrorCode());
            plugin.getLogger().severe("SQLState: " + e.getSQLState());

        }
        for (String denied : plot.getDenied()) {
            try (PreparedStatement ps = getConnection()
                    .prepareStatement("INSERT INTO plotmecore_denied (plot_id, player) VALUES(?,?)")) {
                ps.setLong(1, plot.getInternalID());
                ps.setString(2, denied);
                ps.execute();
                getConnection().commit();
            } catch (SQLException e) {
                plugin.getLogger().severe("Error adding allowed data for plot with internal id " + plot.getInternalID());
                plugin.getLogger().severe("Details: " + e.getMessage());
                plugin.getLogger().severe("Error Code: " + e.getErrorCode());
                plugin.getLogger().severe("SQLState: " + e.getSQLState());

                e.printStackTrace();
            }
        }
        for (Map.Entry<String, Plot.AccessLevel> member : plot.getMembers().entrySet()) {
            try (PreparedStatement ps = getConnection()
                    .prepareStatement("INSERT INTO plotmecore_allowed (plot_id, player, access) VALUES(?,?, ?)")) {
                ps.setLong(1, plot.getInternalID());
                ps.setString(2, member.getKey());
                ps.setInt(3, member.getValue().getLevel());
                ps.execute();
                getConnection().commit();
            } catch (SQLException e) {
                plugin.getLogger().severe("Error adding allowed data for plot with internal id " + plot.getInternalID());
                plugin.getLogger().severe("Details: " + e.getMessage());
                plugin.getLogger().severe("Error Code: " + e.getErrorCode());
                plugin.getLogger().severe("SQLState: " + e.getSQLState());

                e.printStackTrace();
            }
        }
        for (UUID player : plot.getLikers()) {
            try (PreparedStatement ps = getConnection()
                    .prepareStatement("INSERT INTO plotmecore_likes (plot_id, player) VALUES(?, ?)")) {
                ps.setLong(1, plot.getInternalID());
                ps.setString(2, player.toString());
                ps.execute();
                getConnection().commit();
            } catch (SQLException e) {
                plugin.getLogger().severe("Error adding allowed data for plot with internal id " + plot.getInternalID());
                plugin.getLogger().severe("Details: " + e.getMessage());
                plugin.getLogger().severe("Error Code: " + e.getErrorCode());
                plugin.getLogger().severe("SQLState: " + e.getSQLState());
                e.printStackTrace();
            }
        }
        for (Map.Entry<String, Map<String, String>> metadata : plot.getAllPlotProperties().entrySet()) {
            for (Map.Entry<String, String> stringStringEntry : metadata.getValue().entrySet()) {
                try (PreparedStatement ps = getConnection()
                        .prepareStatement("INSERT INTO plotmecore_metadata(plot_id, pluginName, propertyName, "
                                + "propertyValue) VALUES (?,?,?,?)")) {
                    ps.setLong(1, plot.getInternalID());
                    ps.setString(2, metadata.getKey());
                    ps.setString(3, stringStringEntry.getKey());
                    ps.setString(4, stringStringEntry.getValue());
                    ps.execute();
                    getConnection().commit();
                } catch (SQLException e) {
                    plugin.getLogger().severe("Details: " + e.getMessage());
                    plugin.getLogger().severe("Error Code: " + e.getErrorCode());
                    plugin.getLogger().severe("SQLState: " + e.getSQLState());
                    e.printStackTrace();
                }
            }
        }
    }
}
