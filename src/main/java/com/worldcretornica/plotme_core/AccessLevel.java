package com.worldcretornica.plotme_core;

/**
 * Fine-grained per-plot access tiers, ordered by privilege (least to most).
 *
 * <p>Hierarchy: {@link #CONTAINER} &lt; {@link #INTERACT} &lt; {@link #BUILD} &lt; {@link #MANAGE}.
 * Use {@link #includes(AccessLevel)} to test whether this level satisfies a required level
 * (e.g. {@code level.includes(AccessLevel.INTERACT)} is true for BUILD and MANAGE).</p>
 *
 * <p>This enum is the public-facing access model added in v2.0.0. The legacy
 * {@link Plot.AccessLevel} ({@code ALLOWED}, {@code TRUSTED}) is retained for
 * backwards-compatible persistence in the {@code plotmecore_allowed.access} column;
 * see {@link Plot#getAccessLevel(java.util.UUID)} for the mapping.</p>
 *
 * <p>The ordinal of each constant matches the integer stored in the
 * {@code plotmecore_allowed.access} column when written through the new
 * {@code /plotme access} command flow. Reordering these constants would break
 * on-disk data — only append new constants.</p>
 */
public enum AccessLevel {

    /** Can open / use containers (chests, furnaces, hoppers, etc.). */
    CONTAINER,
    /** CONTAINER + can use doors, buttons, levers, plates, and other interactables. */
    INTERACT,
    /** INTERACT + can place and break blocks. Default for added members and trusted players. */
    BUILD,
    /** BUILD + can add/remove other members and change plot settings. */
    MANAGE;

    /**
     * Returns true if this level includes (i.e. is at least as privileged as) {@code other}.
     *
     * <p>Examples:</p>
     * <ul>
     *   <li>{@code BUILD.includes(INTERACT)} -> true</li>
     *   <li>{@code BUILD.includes(CONTAINER)} -> true</li>
     *   <li>{@code INTERACT.includes(BUILD)} -> false</li>
     *   <li>{@code MANAGE.includes(MANAGE)} -> true</li>
     * </ul>
     *
     * @param other the required level being checked; null returns false
     * @return true iff this.ordinal() &gt;= other.ordinal()
     */
    public boolean includes(AccessLevel other) {
        if (other == null) {
            return false;
        }
        return this.ordinal() >= other.ordinal();
    }

    /**
     * Resolves a string (case-insensitive) into an AccessLevel. Accepts the four constant
     * names. Returns null for unrecognised input.
     */
    public static AccessLevel fromString(String name) {
        if (name == null) {
            return null;
        }
        try {
            return AccessLevel.valueOf(name.toUpperCase());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
