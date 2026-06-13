package com.worldcretornica.plotme_core;

import com.worldcretornica.plotme_core.api.IWorld;
import com.worldcretornica.plotme_core.api.Vector;
import com.worldcretornica.plotme_core.flag.PlotFlag;

import java.time.LocalDate;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public class Plot {

    /**
     * Reserved {@code pluginName} value in the per-plot metadata table used
     * to store flag values. Real plugins must not use this string as their
     * own metadata namespace.
     */
    public static final String FLAGS_NAMESPACE = "flags";

    private final HashMap<String, Plot.AccessLevel> allowed = new HashMap<>();
    private final HashSet<String> denied = new HashSet<>();
    private final HashMap<String, Map<String, String>> metadata = new HashMap<>();
    /**
     * PlotIds (within the same world) this plot is merged with. Merging is
     * symmetric -- every member of a merged cluster carries the set of the
     * other members. Persisted in the additive {@code plotmecore_merged}
     * table so existing schemas keep working when this is loaded against
     * older databases.
     *
     * Cross-plot building / clearing for merged plots is intentionally
     * not implemented yet; consumers should treat this as data only, and
     * resolve their own behaviour from it.
     */
    private final HashSet<PlotId> mergedWith = new HashSet<>();
    private final Vector plotTopLoc;
    private final Vector plotBottomLoc;
    private final String createdDate;
    private String owner = "Unknown";
    private UUID ownerId = UUID.randomUUID();
    private IWorld world;
    private String biome = "PLAINS";
    private LocalDate expiredDate = null;
    private boolean finished = false;
    private PlotId id = new PlotId(0, 0);
    private double price = 0.0;
    private boolean forSale = false;
    private String finishedDate = null;
    private boolean protect = false;
    private int likes = 0;
    //defaults to 0 until it is saved to the database
    private long internalID = 0;
    private String plotName;
    private HashSet<UUID> likers = new HashSet<>();

    public Plot(String owner, UUID uuid, IWorld world, PlotId plotId, Vector plotTopLoc, Vector plotBottomLoc) {
        setOwner(owner);
        setOwnerId(uuid);
        setWorld(world);
        setId(plotId);
        this.plotTopLoc = plotTopLoc;
        this.plotBottomLoc = plotBottomLoc;
        createdDate = LocalDate.now().toString();
    }

    public Plot(long internalID, String owner, UUID ownerId, IWorld world, String biome, LocalDate expiredDate,
            HashMap<String, AccessLevel> allowed,
            HashSet<String>
                    denied,
            HashSet<UUID> likers, PlotId id, double price, boolean forSale, boolean finished, String finishedDate, boolean protect,
            Map<String, Map<String, String>> metadata, int plotLikes, String plotName, Vector topLoc, Vector bottomLoc, String createdDate) {
        this.internalID = internalID;
        this.owner = owner;
        this.ownerId = ownerId;
        this.world = world;
        this.biome = biome;
        this.expiredDate = expiredDate;
        this.finished = finished;
        this.finishedDate = finishedDate;
        this.allowed.putAll(allowed);
        this.id = id;
        this.price = price;
        this.forSale = forSale;
        this.finishedDate = finishedDate;
        this.protect = protect;
        this.likers.addAll(likers);
        this.plotName = plotName;
        this.likes = plotLikes;
        this.denied.addAll(denied);
        this.metadata.putAll(metadata);
        this.plotTopLoc = topLoc;
        this.plotBottomLoc = bottomLoc;
        this.createdDate = createdDate;
    }

    public void resetExpire(int days) {
        if (days == 0) {
            if (getExpiredDate() != null) {
                setExpiredDate(null);
            }
        } else {
            LocalDate target = LocalDate.now().plusDays(days);
            if (expiredDate == null || target.isAfter(expiredDate)) {
                expiredDate = target;
            }
        }
    }

    public String getBiome() {
        return biome;
    }

    public final void setBiome(String biome) {
        this.biome = biome;

    }

    public final String getOwner() {
        return owner;
    }

    public final void setOwner(String owner) {
        this.owner = owner;
    }

    public final UUID getOwnerId() {
        return ownerId;
    }

    public final void setOwnerId(UUID uuid) {
        ownerId = uuid;
    }

    public HashSet<String> getDenied() {
        return denied;
    }

    public void addMember(String name, AccessLevel level) {
        if ("*".equals(name)) {
            this.getMembers().clear();
            getMembers().put(name, AccessLevel.ALLOWED);
        } else {
            getMembers().put(name, level);
        }
    }

    public void addDenied(String name) {
        if (!isDeniedInternal(name)) {
            getDenied().add(name);
        }
    }

    public void removeMembers(String name) {
        if (getMembers().containsKey(name)) {
            getMembers().remove(name);
        }
    }

    public void removeMember(String name) {
        if (getMembers().containsKey(name)) {
            getMembers().remove(name);
        }
    }

    public void removeDenied(String name) {
        if (getDenied().contains(name)) {
            getDenied().remove(name);
        }
    }

    public void removeAllMembers() {
        getMembers().clear();
    }

    public void removeAllDenied() {
        getDenied().clear();
    }

    public boolean isDenied(String name) {
        return isDeniedInternal(name);
    }

    public boolean isDenied(UUID uuid) {
        return isDeniedInternal(uuid.toString());
    }

    private boolean isDeniedInternal(String name) {
        return getDenied().contains("*") || getDenied().contains(name);
    }

    /**
     * A map of allowed and trusted players
     * @return allowed and trusted player map
     */
    public HashMap<String, Plot.AccessLevel> getMembers() {
        return allowed;
    }

    public final IWorld getWorld() {
        return world;
    }

    public final void setWorld(IWorld world) {
        this.world = world;
    }

    public final LocalDate getExpiredDate() {
        return expiredDate;
    }

    public final void setExpiredDate(LocalDate expiredDate) {
        this.expiredDate = expiredDate;
    }

    public final boolean isFinished() {
        return finished;
    }

    public final void setFinished(boolean finished) {
        this.finished = finished;
        if (finished) {
            setFinishedDate(LocalDate.now().toString());
        } else {
            setFinishedDate(null);
        }
    }

    public final PlotId getId() {
        return id;
    }

    public final void setId(PlotId id) {
        this.id = id;
    }

    /**
     * Retrieves the price of the plot.
     * If {@link #isForSale()} is false then this should return 0
     * @return the price of the plot
     */
    public final double getPrice() {
        return price;
    }

    public final void setPrice(double price) {
        this.price = price;
    }

    /**
     * Checks if this plot is able to be sold
     * @return true if it is for sale, false otherwise
     */
    public final boolean isForSale() {
        return forSale;
    }

    /**
     * Sets if this plot can be sold or not
     * @param forSale true if it can be sold, false if it cannot be sold
     */
    public final void setForSale(boolean forSale) {
        this.forSale = forSale;

    }

    public final String getFinishedDate() {
        return finishedDate;
    }

    private void setFinishedDate(String finishedDate) {
        this.finishedDate = finishedDate;

    }

    public final boolean isProtected() {
        return protect;
    }

    public final void setProtected(boolean protect) {
        this.protect = protect;
    }

    public String getPlotProperty(String pluginname, String property) {
        return metadata.get(pluginname).get(property);
    }

    public boolean setPlotProperty(String pluginname, String property, String value) {
        if (!metadata.containsKey(pluginname)) {
            metadata.put(pluginname, new HashMap<String, String>());
        }
        metadata.get(pluginname).put(property, value);
        return true;
    }

    public Map<String, Map<String, String>> getAllPlotProperties() {
        return metadata;
    }

    // ------------------------------------------------------------------
    // Per-plot flags. Backed by the same metadata map / plotmecore_metadata
    // table; we just reserve the {@link #FLAGS_NAMESPACE} plugin name for
    // ourselves. This keeps the schema unchanged while giving us a typed
    // API.
    // ------------------------------------------------------------------

    /**
     * Resolve a flag value, falling back to the flag's default when this
     * plot has not explicitly set it. Never returns {@code null} as long as
     * the flag itself supplied a non-null default.
     */
    public <T> T getFlagValue(PlotFlag<T> flag) {
        Map<String, String> flagMap = metadata.get(FLAGS_NAMESPACE);
        if (flagMap == null) {
            return flag.getDefaultValue();
        }
        String raw = flagMap.get(flag.getName());
        if (raw == null) {
            return flag.getDefaultValue();
        }
        try {
            return flag.parse(raw);
        } catch (RuntimeException ex) {
            // Corrupted value -- treat as unset rather than killing the
            // event handler that called us.
            return flag.getDefaultValue();
        }
    }

    /**
     * @return {@code true} if this plot has an explicit value stored for
     *         the given flag (i.e. it differs from "use default" purely by
     *         being present, regardless of equality with the default).
     */
    public boolean hasFlagValue(PlotFlag<?> flag) {
        Map<String, String> flagMap = metadata.get(FLAGS_NAMESPACE);
        return flagMap != null && flagMap.containsKey(flag.getName());
    }

    /**
     * Store an explicit value for the given flag. Passing the same value as
     * {@link PlotFlag#getDefaultValue()} still records an explicit entry --
     * the caller can use {@link #resetFlagValue(PlotFlag)} if they wanted to
     * fall back to the default.
     */
    public <T> void setFlagValue(PlotFlag<T> flag, T value) {
        setPlotProperty(FLAGS_NAMESPACE, flag.getName(), flag.serialize(value));
    }

    /**
     * Clear the explicit value, so subsequent reads return the flag's
     * default. No-op if the flag was already unset.
     */
    public void resetFlagValue(PlotFlag<?> flag) {
        Map<String, String> flagMap = metadata.get(FLAGS_NAMESPACE);
        if (flagMap == null) {
            return;
        }
        flagMap.remove(flag.getName());
        if (flagMap.isEmpty()) {
            metadata.remove(FLAGS_NAMESPACE);
        }
    }

    /**
     * Read-only view of all explicitly-set flag values for this plot, keyed
     * by flag name. Useful for {@code /plotme flag} listings when no
     * specific flag was requested.
     */
    public Map<String, String> getAllFlagValues() {
        Map<String, String> flagMap = metadata.get(FLAGS_NAMESPACE);
        if (flagMap == null) {
            return Collections.emptyMap();
        }
        return Collections.unmodifiableMap(flagMap);
    }

    /**
     * Retrieves the unique internal id for this plot.
     * Commonly used for database lookups and debugging.
     * Normal users should not be concerned about this number nor should they need to see it.
     * @return unique internal id
     */
    public long getInternalID() {
        return internalID;
    }

    /**
     * Sets the unique internal id for this plot.
     * @param internalID unique long value
     */
    public void setInternalID(long internalID) {
        this.internalID = internalID;
    }

    public int getLikes() {
        return likes;
    }

    public void setLikes(int likes) {
        this.likes = likes;
    }

    public void addLike(int likes, UUID player) {
        this.getLikers().add(player);
        this.likes = likes;
    }

    public String getPlotName() {
        return plotName;
    }

    public void setPlotName(String plotName) {
        this.plotName = plotName;
    }

    /**
     * Do not use for teleporting players. It will suffocate or kill them.
     * @return
     */
    public Vector getMiddle() {
        Vector bottom = plotBottomLoc;
        Vector top = plotTopLoc;

        double x = (top.getX() + bottom.getX() + 1) / 2;
        double z = (top.getZ() + bottom.getZ() + 1) / 2;


        return new Vector(x, 0, z);
    }

    public int getTopX() {
        return plotTopLoc.getBlockX();
    }

    public int getTopZ() {
        return plotTopLoc.getBlockZ();
    }

    public Vector getPlotTopLoc() {
        return plotTopLoc;
    }

    public Vector getPlotBottomLoc() {
        return plotBottomLoc;
    }

    public int getBottomX() {
        return plotBottomLoc.getBlockX();
    }

    public int getBottomZ() {
        return plotBottomLoc.getBlockZ();
    }

    public String getCreatedDate() {
        return createdDate;
    }

    public void addDenied(HashSet<String> denied) {
        this.denied.addAll(denied);
    }

    public void addMembers(HashMap<String, AccessLevel> allowed) {
        this.allowed.putAll(allowed);
    }

    /**
     * The live set of plot ids this plot is linked with. Returned by reference
     * so persistence / merge code can mutate it directly; callers that only
     * want to inspect should treat it as read-only.
     */
    public HashSet<PlotId> getMergedWith() {
        return mergedWith;
    }

    /**
     * Link this plot with another. Does not mutate the other plot — callers
     * that need a symmetric link must call {@code addMergedWith} on both
     * sides (the {@code PlotMeCoreManager#linkMergedPlots} helper does this
     * in one call).
     */
    public void addMergedWith(PlotId other) {
        if (other != null && !other.equals(this.id)) {
            this.mergedWith.add(other);
        }
    }

    public void removeMergedWith(PlotId other) {
        this.mergedWith.remove(other);
    }

    /** Bulk load entry point used by {@link com.worldcretornica.plotme_core.storage.Database}. */
    public void addMergedWith(java.util.Collection<PlotId> ids) {
        for (PlotId other : ids) {
            addMergedWith(other);
        }
    }

    public boolean isMergedWith(PlotId other) {
        return mergedWith.contains(other);
    }

    /**
     * Gets a set of players who have liked this plot
     * @return
     */
    public HashSet<UUID> getLikers() {
        return likers;
    }

    public void setLikers(HashSet<UUID> likers) {
        this.likers = likers;
    }

    //todo test equals to make sure it is reliable.
    @Override public boolean equals(Object obj) {
        if (obj instanceof Plot) {
            Plot obj1 = (Plot) obj;
            if (obj1.getInternalID() == this.internalID) {
                if (obj1.getId().equals(this.id)) {
                    if (obj1.getOwnerId().equals(this.ownerId)) {
                        if (obj1.getWorld().equals(this.world)) {
                            if (Objects.equals(obj1.getExpiredDate(), this.expiredDate)) {
                                return true;
                            }
                        }
                    }
                }
            }

        }
        return false;
    }

    @Override public int hashCode() {
        return Objects.hash(internalID, id, ownerId, world, expiredDate);
    }

    public boolean canPlayerLike(UUID uniqueId) {
        return !likers.contains(uniqueId);
    }

    public void removeLike(int i, UUID uniqueId) {
        likes -= i;
        likers.remove(uniqueId);
    }

    public Optional<AccessLevel> isMember(String allowed) {
        if (getMembers().containsKey("*")) {
            return Optional.of(AccessLevel.ALLOWED);
        } else {
            return Optional.ofNullable(getMembers().get(allowed));
        }
    }

    public Optional<AccessLevel> isMember(UUID uniqueId) {
        return isMember(uniqueId.toString());
    }

    /**
     * Returns the fine-grained {@link com.worldcretornica.plotme_core.AccessLevel} for the given
     * player on this plot.
     *
     * <p>Resolution order:</p>
     * <ol>
     *   <li>Owner -&gt; {@link com.worldcretornica.plotme_core.AccessLevel#MANAGE}.</li>
     *   <li>An explicit per-player override stored under the {@code plotme_core_access}
     *       metadata namespace (set by the {@code /plotme access} command) -&gt; that level.</li>
     *   <li>Wildcard ("*") explicit override under the same namespace -&gt; that level.</li>
     *   <li>Legacy membership: present in the {@code allowed} map (either as ALLOWED or TRUSTED,
     *       or via the "*" wildcard) -&gt; {@link com.worldcretornica.plotme_core.AccessLevel#BUILD}.
     *       The spec maps both legacy tiers to BUILD; MANAGE / INTERACT / CONTAINER can only be
     *       reached via the explicit metadata override above.</li>
     *   <li>Otherwise -&gt; {@code null} (default deny).</li>
     * </ol>
     *
     * @param uuid player UUID; null returns null
     * @return resolved access level, or null if the player has no access
     */
    public com.worldcretornica.plotme_core.AccessLevel getAccessLevel(UUID uuid) {
        if (uuid == null) {
            return null;
        }
        if (uuid.equals(ownerId)) {
            return com.worldcretornica.plotme_core.AccessLevel.MANAGE;
        }
        Map<String, String> overrides = metadata.get("plotme_core_access");
        if (overrides != null) {
            String explicit = overrides.get(uuid.toString());
            if (explicit != null) {
                com.worldcretornica.plotme_core.AccessLevel parsed =
                        com.worldcretornica.plotme_core.AccessLevel.fromString(explicit);
                if (parsed != null) {
                    return parsed;
                }
            }
            String wildcard = overrides.get("*");
            if (wildcard != null) {
                com.worldcretornica.plotme_core.AccessLevel parsed =
                        com.worldcretornica.plotme_core.AccessLevel.fromString(wildcard);
                if (parsed != null) {
                    return parsed;
                }
            }
        }
        // Fall back to legacy add/trust membership: both map to BUILD per v2.0.0 spec.
        if (isMember(uuid).isPresent()) {
            return com.worldcretornica.plotme_core.AccessLevel.BUILD;
        }
        return null;
    }

    @Override public String toString() {
        return "Plot{" +
                "allowed=" + allowed +
                ", denied=" + denied +
                ", metadata=" + metadata +
                ", plotTopLoc=" + plotTopLoc +
                ", plotBottomLoc=" + plotBottomLoc +
                ", createdDate='" + createdDate + '\'' +
                ", owner='" + owner + '\'' +
                ", ownerId=" + ownerId +
                ", world=" + world +
                ", biome='" + biome + '\'' +
                ", expiredDate=" + expiredDate +
                ", finished=" + finished +
                ", id=" + id +
                ", price=" + price +
                ", forSale=" + forSale +
                ", finishedDate='" + finishedDate + '\'' +
                ", protect=" + protect +
                ", likes=" + likes +
                ", internalID=" + internalID +
                ", plotName='" + plotName + '\'' +
                ", likers=" + likers +
                '}';
    }


    public enum AccessLevel {
        ALLOWED(0),
        TRUSTED(1);

        private final int level;

        AccessLevel(int accessLevel) {
            level = accessLevel;
        }

        public static AccessLevel getAccessLevel(int level) {
            switch (level) {
                case 0:
                    return ALLOWED;
                case 1:
                    return TRUSTED;
                default:
                    return ALLOWED;
            }
        }

        public int getLevel() {
            return level;
        }
    }
}