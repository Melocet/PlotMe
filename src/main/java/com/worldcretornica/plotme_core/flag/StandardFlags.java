package com.worldcretornica.plotme_core.flag;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Built-in flag definitions. All static references here are the canonical
 * handles other code should hold (e.g. the Bukkit listeners pass these to
 * {@code Plot#getFlagValue(PlotFlag)}). The registry merely keeps the
 * by-name lookup table in sync.
 *
 * Adding a new flag here is the only step needed for it to be persisted and
 * exposed via {@code /plotme flag}. Wiring the gameplay effect (cancelling
 * the relevant event, etc.) is done in the listener.
 */
public final class StandardFlags {

    // ----- Boolean flags ---------------------------------------------------

    /** Whether players can damage other players inside the plot. */
    public static final PlotFlag<Boolean> PVP = new BooleanFlag("pvp", true);

    /** Master switch for all natural creature spawning. */
    public static final PlotFlag<Boolean> MOB_SPAWNING = new BooleanFlag("mob-spawning", true);

    /** Whether hostile mobs may spawn. Checked only if MOB_SPAWNING is true. */
    public static final PlotFlag<Boolean> MONSTER_SPAWNING = new BooleanFlag("monster-spawning", true);

    /** Whether passive/animal mobs may spawn. Checked only if MOB_SPAWNING is true. */
    public static final PlotFlag<Boolean> ANIMAL_SPAWNING = new BooleanFlag("animal-spawning", true);

    /** Whether fire may spread / blocks may catch fire from neighbours. */
    public static final PlotFlag<Boolean> FIRE_SPREAD = new BooleanFlag("fire-spread", true);

    /** Whether explosions damage blocks/entities inside the plot. */
    public static final PlotFlag<Boolean> EXPLOSION = new BooleanFlag("explosion", true);

    /** Whether non-creative players may toggle flight while inside the plot. */
    public static final PlotFlag<Boolean> FLY = new BooleanFlag("fly", true);

    /** Whether players may drop items inside the plot. */
    public static final PlotFlag<Boolean> ITEM_DROP = new BooleanFlag("item-drop", true);

    /** Whether players may pick up dropped items inside the plot. */
    public static final PlotFlag<Boolean> ITEM_PICKUP = new BooleanFlag("item-pickup", true);

    /** Whether hostile mobs may damage players inside the plot. */
    public static final PlotFlag<Boolean> HOSTILE_MOB_ATTACK = new BooleanFlag("hostile-mob-attack", true);

    /** Whether blocks may burn away from fire inside the plot. */
    public static final PlotFlag<Boolean> BLOCK_BURN = new BooleanFlag("block-burn", true);

    /** Whether leaves may decay naturally inside the plot. */
    public static final PlotFlag<Boolean> LEAF_DECAY = new BooleanFlag("leaf-decay", true);

    // ----- Integer flag ----------------------------------------------------

    /**
     * Client-side time of day shown to players inside the plot.
     * Range: 0..23999 (Minecraft tick of day). -1 means "let the world
     * decide" (the player's time is reset to natural).
     */
    public static final PlotFlag<Integer> TIME = new PlotFlag<Integer>("time", -1) {
        @Override
        public Integer parse(String input) {
            try {
                int v = Integer.parseInt(input.trim());
                if (v != -1 && (v < 0 || v > 24000)) {
                    throw new IllegalArgumentException("time must be -1 or in 0..24000");
                }
                return v;
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException("time must be an integer");
            }
        }

        @Override
        public String serialize(Integer value) {
            return Integer.toString(value);
        }

        @Override
        public String getValueHint() {
            return "<0..24000 | -1>";
        }
    };

    // ----- String flag -----------------------------------------------------

    /**
     * Client-side weather shown to players inside the plot. One of
     * {@code clear}, {@code rain}, {@code storm}, or {@code unset} (let the
     * world decide).
     */
    public static final PlotFlag<String> WEATHER = new PlotFlag<String>("weather", "unset") {
        private final Set<String> allowed = new LinkedHashSet<>(Arrays.asList("clear", "rain", "storm", "unset"));

        @Override
        public String parse(String input) {
            String v = input.trim().toLowerCase(Locale.ROOT);
            if (!allowed.contains(v)) {
                throw new IllegalArgumentException("weather must be one of " + allowed);
            }
            return v;
        }

        @Override
        public String serialize(String value) {
            return value;
        }

        @Override
        public String getValueHint() {
            return "<clear|rain|storm|unset>";
        }
    };

    // ----- List flag -------------------------------------------------------

    /**
     * Block material names (as their canonical Bukkit {@code Material.name()}
     * strings) that even plot members are forbidden from right-clicking.
     * Stored as a comma-separated list. We keep the values as raw uppercase
     * strings rather than {@code org.bukkit.Material} so that this class
     * stays free of Bukkit imports -- the listener does the lookup.
     */
    public static final PlotFlag<List<String>> USE_DENY = new PlotFlag<List<String>>("use-deny", Collections.<String>emptyList()) {
        @Override
        public List<String> parse(String input) {
            String trimmed = input.trim();
            if (trimmed.isEmpty()) {
                return Collections.emptyList();
            }
            String[] parts = trimmed.split("\\s*,\\s*");
            List<String> out = new ArrayList<>(parts.length);
            for (String p : parts) {
                if (p.isEmpty()) {
                    continue;
                }
                out.add(p.toUpperCase(Locale.ROOT));
            }
            return out;
        }

        @Override
        public String serialize(List<String> value) {
            if (value == null || value.isEmpty()) {
                return "";
            }
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < value.size(); i++) {
                if (i > 0) {
                    sb.append(',');
                }
                sb.append(value.get(i));
            }
            return sb.toString();
        }

        @Override
        public String getValueHint() {
            return "<MATERIAL,MATERIAL,...>";
        }
    };

    private StandardFlags() {
    }

    /**
     * Register every built-in flag with the global {@link PlotFlagRegistry}.
     * Called once via {@link PlotFlagRegistry#ensureDefaultsRegistered()}.
     */
    static void registerDefaults() {
        PlotFlagRegistry.register(PVP);
        PlotFlagRegistry.register(MOB_SPAWNING);
        PlotFlagRegistry.register(MONSTER_SPAWNING);
        PlotFlagRegistry.register(ANIMAL_SPAWNING);
        PlotFlagRegistry.register(FIRE_SPREAD);
        PlotFlagRegistry.register(EXPLOSION);
        PlotFlagRegistry.register(FLY);
        PlotFlagRegistry.register(ITEM_DROP);
        PlotFlagRegistry.register(ITEM_PICKUP);
        PlotFlagRegistry.register(HOSTILE_MOB_ATTACK);
        PlotFlagRegistry.register(BLOCK_BURN);
        PlotFlagRegistry.register(LEAF_DECAY);
        PlotFlagRegistry.register(TIME);
        PlotFlagRegistry.register(WEATHER);
        PlotFlagRegistry.register(USE_DENY);
    }

    /**
     * Concrete {@link PlotFlag} for a simple true/false toggle. Accepts the
     * common synonyms (true/false, yes/no, on/off, allow/deny, 1/0).
     */
    private static final class BooleanFlag extends PlotFlag<Boolean> {

        BooleanFlag(String name, boolean defaultValue) {
            super(name, defaultValue);
        }

        @Override
        public Boolean parse(String input) {
            String v = input.trim().toLowerCase(Locale.ROOT);
            switch (v) {
                case "true":
                case "yes":
                case "on":
                case "allow":
                case "1":
                    return Boolean.TRUE;
                case "false":
                case "no":
                case "off":
                case "deny":
                case "0":
                    return Boolean.FALSE;
                default:
                    throw new IllegalArgumentException("expected true/false");
            }
        }

        @Override
        public String serialize(Boolean value) {
            return value ? "true" : "false";
        }

        @Override
        public String getValueHint() {
            return "<true|false>";
        }
    }
}
