package com.worldcretornica.plotme.defaultgenerator;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.data.BlockData;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Parses block strings from world configs. Accepts either:
 *   - Modern Material/BlockData syntax: "OAK_PLANKS", "minecraft:smooth_stone_slab[type=top]"
 *   - Legacy numeric id[:data]: "44:7", "5", "3"
 *
 * The numeric form maps a small subset of common pre-1.13 IDs used by
 * PlotMe-DefaultGenerator's defaults so old config.yml files keep working.
 * Unknown numeric values fall back to STONE.
 */
public final class MaterialParser {

    private MaterialParser() {}

    private static final Material FALLBACK = Material.STONE;

    /**
     * Maps "<id>:<data>" — only the combinations used by the default
     * PlotMe road/wall config are populated. Add more on demand.
     */
    private static final Map<String, Material> LEGACY = new HashMap<>();

    static {
        // wall/slab variants — id 44 was the "stone double slab" group
        LEGACY.put("44",   Material.SMOOTH_STONE_SLAB);
        LEGACY.put("44:0", Material.SMOOTH_STONE_SLAB);
        LEGACY.put("44:1", Material.SANDSTONE_SLAB);
        LEGACY.put("44:4", Material.BRICK_SLAB);
        LEGACY.put("44:7", Material.QUARTZ_SLAB);

        // road blocks — id 5 was wooden planks
        LEGACY.put("5",   Material.OAK_PLANKS);
        LEGACY.put("5:0", Material.OAK_PLANKS);
        LEGACY.put("5:1", Material.SPRUCE_PLANKS);
        LEGACY.put("5:2", Material.BIRCH_PLANKS);
        LEGACY.put("5:3", Material.JUNGLE_PLANKS);
        LEGACY.put("5:4", Material.ACACIA_PLANKS);
        LEGACY.put("5:5", Material.DARK_OAK_PLANKS);

        // plot floor — id 2 was grass block
        LEGACY.put("2",   Material.GRASS_BLOCK);

        // fill — id 3 was dirt
        LEGACY.put("3",   Material.DIRT);
        LEGACY.put("3:1", Material.COARSE_DIRT);
        LEGACY.put("3:2", Material.PODZOL);

        // some commonly-seen alternates from older PlotMe configs
        LEGACY.put("1",   Material.STONE);
        LEGACY.put("4",   Material.COBBLESTONE);
        LEGACY.put("7",   Material.BEDROCK);
        LEGACY.put("12",  Material.SAND);
        LEGACY.put("17",  Material.OAK_LOG);
        LEGACY.put("24",  Material.SANDSTONE);
        LEGACY.put("35",  Material.WHITE_WOOL);
        LEGACY.put("49",  Material.OBSIDIAN);
        LEGACY.put("57",  Material.DIAMOND_BLOCK);
        LEGACY.put("87",  Material.NETHERRACK);
        LEGACY.put("89",  Material.GLOWSTONE);
        LEGACY.put("98",  Material.STONE_BRICKS);
        LEGACY.put("155", Material.QUARTZ_BLOCK);
    }

    public static Material parseMaterial(String raw) {
        if (raw == null || raw.isEmpty()) return FALLBACK;
        String trimmed = raw.trim();

        if (Character.isDigit(trimmed.charAt(0))) {
            Material m = LEGACY.get(trimmed);
            if (m != null) return m;
            int colon = trimmed.indexOf(':');
            if (colon >= 0) {
                Material base = LEGACY.get(trimmed.substring(0, colon));
                if (base != null) return base;
            }
            return FALLBACK;
        }

        String name = trimmed;
        if (name.startsWith("minecraft:")) {
            name = name.substring("minecraft:".length());
        }
        int bracket = name.indexOf('[');
        if (bracket >= 0) name = name.substring(0, bracket);
        try {
            Material m = Material.matchMaterial(name);
            if (m != null) return m;
        } catch (Exception ignore) {}
        return FALLBACK;
    }

    /**
     * Parse a full BlockData from a config string. Supports bracketed
     * property syntax like "OAK_SLAB[type=top]" or "minecraft:..".
     * Numeric legacy ids resolve to the Material's default block data.
     */
    public static BlockData parseBlockData(String raw) {
        if (raw == null || raw.isEmpty()) return FALLBACK.createBlockData();
        String trimmed = raw.trim();

        if (Character.isDigit(trimmed.charAt(0))) {
            return parseMaterial(trimmed).createBlockData();
        }

        String name = trimmed;
        if (!name.contains(":")) name = "minecraft:" + name.toLowerCase(Locale.ROOT);
        try {
            return org.bukkit.Bukkit.createBlockData(name);
        } catch (IllegalArgumentException ex) {
            return parseMaterial(trimmed).createBlockData();
        }
    }
}
