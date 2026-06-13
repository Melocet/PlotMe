package com.worldcretornica.plotme_core.flag;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Process-wide registry of {@link PlotFlag} instances. The set of built-in
 * flags ({@link StandardFlags#registerDefaults()}) is registered once at
 * startup; third-party code may add more via {@link #register(PlotFlag)}.
 *
 * Iteration order is insertion order, so {@code /plotme flag} listings
 * mirror the order flags were registered in (built-ins first).
 */
public final class PlotFlagRegistry {

    private static final Map<String, PlotFlag<?>> FLAGS = new LinkedHashMap<>();
    private static boolean defaultsRegistered = false;

    private PlotFlagRegistry() {
    }

    /**
     * Register a flag. Throws if a flag with the same name is already
     * registered -- flag names must be globally unique because they are also
     * used as metadata keys in the database.
     */
    public static synchronized void register(PlotFlag<?> flag) {
        String key = flag.getName().toLowerCase(Locale.ROOT);
        if (FLAGS.containsKey(key)) {
            throw new IllegalStateException("Flag '" + key + "' already registered");
        }
        FLAGS.put(key, flag);
    }

    /**
     * Look up a flag by name (case-insensitive). Returns {@code null} if no
     * flag with that name exists.
     */
    public static PlotFlag<?> get(String name) {
        if (name == null) {
            return null;
        }
        return FLAGS.get(name.toLowerCase(Locale.ROOT));
    }

    /**
     * Read-only view of every registered flag in registration order.
     */
    public static Collection<PlotFlag<?>> all() {
        return Collections.unmodifiableCollection(FLAGS.values());
    }

    /**
     * Idempotently register the built-in flag set. Safe to call multiple
     * times -- only the first call actually does work.
     */
    public static synchronized void ensureDefaultsRegistered() {
        if (defaultsRegistered) {
            return;
        }
        defaultsRegistered = true;
        StandardFlags.registerDefaults();
    }
}
