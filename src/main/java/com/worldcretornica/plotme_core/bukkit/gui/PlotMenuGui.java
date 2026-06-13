package com.worldcretornica.plotme_core.bukkit.gui;

import com.worldcretornica.plotme_core.Plot;
import com.worldcretornica.plotme_core.PlotMeCoreManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Chest-GUI for the plot the player is currently standing on.
 * Construction looks up the plot once; the listener re-reads it on every click
 * (state may have changed via commands while the menu was open).
 *
 * The GUI has two pages: a {@link Page#MAIN} status page and a
 * {@link Page#BIOMES} biome-selector page. Both reuse the same 27-slot
 * inventory; switching pages just clears and re-populates the icons. We
 * deliberately keep one inventory instance so the open-inventory handle
 * stays valid across page swaps.
 *
 * MAIN slot layout (3-row inventory):
 *  4  PLAYER_HEAD  — owner skin + plot ID lore
 *  11 GRASS_BLOCK  — biome (click: opens BIOMES page)
 *  12 PAPER        — members (left: hint /plotme add, right: hint /plotme remove)
 *  13 REDSTONE     — denied (left: hint /plotme deny, right: hint /plotme undeny)
 *  14 LEVER        — toggle protect
 *  15 GOLD_INGOT   — toggle for-sale
 *  22 NETHER_STAR  — teleport home
 *
 * BIOMES slot layout: curated biome icons in slots 0-15 (up to 16 entries),
 * slot 22 BARRIER acts as a "Back" button to return to MAIN.
 */
public final class PlotMenuGui implements InventoryHolder {

    public static final String TITLE_PREFIX = "Plot: ";
    public static final String TITLE_BIOME_SUFFIX = " — Biome";
    public static final int SIZE = 27;

    // MAIN-page slot constants (public so the listener can reference them
    // without copy-paste).
    public static final int SLOT_OWNER    = 4;
    public static final int SLOT_BIOME    = 11;
    public static final int SLOT_MEMBERS  = 12;
    public static final int SLOT_DENIED   = 13;
    public static final int SLOT_PROTECT  = 14;
    public static final int SLOT_FORSALE  = 15;
    public static final int SLOT_HOME     = 22;

    // BIOMES-page slot constants. Biome buttons occupy slots 0..15, the back
    // button sits at slot 22 (matches the HOME slot location on the main page
    // so the bottom-row visual rhythm is preserved).
    public static final int SLOT_BIOME_BACK = 22;

    /**
     * Pages the menu can show. The inventory instance is the same across
     * pages — only the contents (and title) differ when we re-render.
     */
    public enum Page {
        MAIN,
        BIOMES
    }

    /**
     * A single curated biome entry: the canonical biome key (matches
     * {@code Biome#getKey().getKey()}), a display label for the GUI, and the
     * icon material. Keys are passed straight into the same
     * {@code serverBridge.getBiome(...)} validator used by {@code /plotme biome
     * <name>}, so anything that fails to resolve at runtime is logged and
     * skipped rather than crashing the menu.
     */
    public static final class BiomeEntry {
        public final String key;
        public final String displayName;
        public final Material icon;

        public BiomeEntry(String key, String displayName, Material icon) {
            this.key = key;
            this.displayName = displayName;
            this.icon = icon;
        }
    }

    /**
     * Curated set of common biomes shown in the GUI. The full Biome enum has
     * 60+ values which would not fit a single chest page; this list covers
     * the major categories with sensible icons. Order is preserved in the
     * GUI grid (left-to-right, top-to-bottom).
     */
    public static final List<BiomeEntry> CURATED_BIOMES = Collections.unmodifiableList(Arrays.asList(
            new BiomeEntry("plains",            "Plains",            Material.GRASS_BLOCK),
            new BiomeEntry("forest",            "Forest",            Material.OAK_LOG),
            new BiomeEntry("birch_forest",      "Birch Forest",      Material.BIRCH_LOG),
            new BiomeEntry("dark_forest",       "Dark Forest",       Material.DARK_OAK_LOG),
            new BiomeEntry("jungle",            "Jungle",            Material.JUNGLE_LOG),
            new BiomeEntry("savanna",           "Savanna",           Material.ACACIA_LOG),
            new BiomeEntry("desert",            "Desert",            Material.SAND),
            new BiomeEntry("badlands",          "Badlands",          Material.RED_SAND),
            new BiomeEntry("swamp",             "Swamp",             Material.LILY_PAD),
            new BiomeEntry("mangrove_swamp",    "Mangrove Swamp",    Material.MANGROVE_LOG),
            new BiomeEntry("taiga",             "Taiga",             Material.SPRUCE_LOG),
            new BiomeEntry("snowy_taiga",       "Snowy Taiga",       Material.SNOW_BLOCK),
            new BiomeEntry("snowy_plains",      "Snowy Plains",      Material.POWDER_SNOW_BUCKET),
            new BiomeEntry("ocean",             "Ocean",             Material.WATER_BUCKET),
            new BiomeEntry("warm_ocean",        "Warm Ocean",        Material.TROPICAL_FISH),
            new BiomeEntry("mushroom_fields",   "Mushroom Fields",   Material.RED_MUSHROOM_BLOCK)
    ));

    private final UUID viewer;
    private final String worldName;
    private final String plotIdString;
    private final Inventory inventory;
    private Page page;
    // Maps slot index to biome key while the BIOMES page is shown; null on MAIN.
    private Map<Integer, BiomeEntry> biomeSlots;

    public PlotMenuGui(Player viewer, Plot plot) {
        this.viewer = viewer.getUniqueId();
        this.worldName = plot.getWorld().getName();
        this.plotIdString = plot.getId().toString();
        this.inventory = Bukkit.createInventory(this, SIZE, TITLE_PREFIX + plotIdString);
        this.page = Page.MAIN;
        rebuild(plot);
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public UUID getViewer() {
        return viewer;
    }

    public String getWorldName() {
        return worldName;
    }

    public String getPlotIdString() {
        return plotIdString;
    }

    public Page getPage() {
        return page;
    }

    /**
     * Returns the biome entry mapped to a given slot on the BIOMES page, or
     * null if the slot is empty / we're on the MAIN page. Used by the click
     * listener to dispatch biome-button clicks.
     */
    public BiomeEntry getBiomeAt(int slot) {
        if (page != Page.BIOMES || biomeSlots == null) {
            return null;
        }
        return biomeSlots.get(slot);
    }

    /**
     * Re-resolves the plot from the manager (state may have changed) and
     * returns it. Returns null if the plot no longer exists / was reset.
     */
    public Plot resolvePlot() {
        PlotMeCoreManager mgr = PlotMeCoreManager.getInstance();
        com.worldcretornica.plotme_core.api.IWorld iw = mgr.getWorld(worldName);
        if (iw == null) {
            return null;
        }
        if (!com.worldcretornica.plotme_core.PlotId.isValidID(plotIdString)) {
            return null;
        }
        return mgr.getPlotById(new com.worldcretornica.plotme_core.PlotId(plotIdString), iw);
    }

    /** Rebuilds the currently-selected page from the given plot. */
    public void rebuild(Plot plot) {
        if (page == Page.BIOMES) {
            rebuildBiomePage(plot);
        } else {
            rebuildMainPage(plot);
        }
    }

    /** Switch to the BIOMES page and repopulate. */
    public void showBiomePage(Plot plot) {
        this.page = Page.BIOMES;
        rebuildBiomePage(plot);
    }

    /** Switch back to the MAIN page and repopulate. */
    public void showMainPage(Plot plot) {
        this.page = Page.MAIN;
        rebuildMainPage(plot);
    }

    // ---------- page builders ----------

    private void rebuildMainPage(Plot plot) {
        biomeSlots = null;
        inventory.clear();
        inventory.setItem(SLOT_OWNER,   buildOwnerHead(plot));
        inventory.setItem(SLOT_BIOME,   buildBiomeIcon(plot));
        inventory.setItem(SLOT_MEMBERS, buildMembersIcon(plot));
        inventory.setItem(SLOT_DENIED,  buildDeniedIcon(plot));
        inventory.setItem(SLOT_PROTECT, buildProtectIcon(plot));
        inventory.setItem(SLOT_FORSALE, buildForSaleIcon(plot));
        inventory.setItem(SLOT_HOME,    buildHomeIcon(plot));
    }

    private void rebuildBiomePage(Plot plot) {
        inventory.clear();
        Map<Integer, BiomeEntry> slots = new LinkedHashMap<>();
        String current = plot.getBiome();
        int slot = 0;
        for (BiomeEntry entry : CURATED_BIOMES) {
            if (slot >= 16) {
                // Safety: should never trigger with curated list of 16, but
                // bounds-protects future edits.
                break;
            }
            ItemStack icon = buildBiomeButton(entry, current);
            inventory.setItem(slot, icon);
            slots.put(slot, entry);
            slot++;
        }
        biomeSlots = slots;
        inventory.setItem(SLOT_BIOME_BACK, buildBackButton());
    }

    // ---------- icon factories ----------

    private ItemStack buildOwnerHead(Plot plot) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD, 1);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        if (meta != null) {
            OfflinePlayer owner = Bukkit.getOfflinePlayer(plot.getOwnerId());
            meta.setOwningPlayer(owner);
            meta.setDisplayName("§e" + plot.getOwner());
            List<String> lore = new ArrayList<>();
            lore.add("§7Plot ID: §f" + plot.getId());
            lore.add("§7World: §f" + plot.getWorld().getName());
            if (plot.getPlotName() != null && !plot.getPlotName().isEmpty()) {
                lore.add("§7Name: §f" + plot.getPlotName());
            }
            lore.add("§7Created: §f" + plot.getCreatedDate());
            lore.add("§7Likes: §f" + plot.getLikes());
            meta.setLore(lore);
            head.setItemMeta(meta);
        }
        return head;
    }

    private ItemStack buildBiomeIcon(Plot plot) {
        ItemStack item = new ItemStack(Material.GRASS_BLOCK);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§aBiome");
            List<String> lore = new ArrayList<>();
            lore.add("§7Current: §f" + plot.getBiome());
            lore.add("§8Click: §7open biome list");
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack buildMembersIcon(Plot plot) {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§bMembers (" + plot.getMembers().size() + ")");
            List<String> lore = new ArrayList<>();
            lore.add("§8Left-click: §7hint /plotme add <name>");
            lore.add("§8Right-click: §7hint /plotme remove <name>");
            if (plot.getMembers().isEmpty()) {
                lore.add("§7(none)");
            } else if (plot.getMembers().containsKey("*")) {
                lore.add("§f*");
            } else {
                int shown = 0;
                for (Map.Entry<String, Plot.AccessLevel> e : plot.getMembers().entrySet()) {
                    if (shown >= 5) {
                        lore.add("§8...");
                        break;
                    }
                    String name = resolveName(e.getKey());
                    lore.add("§f" + name + " §8(" + e.getValue() + ")");
                    shown++;
                }
            }
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack buildDeniedIcon(Plot plot) {
        ItemStack item = new ItemStack(Material.REDSTONE);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§cDenied (" + plot.getDenied().size() + ")");
            List<String> lore = new ArrayList<>();
            lore.add("§8Left-click: §7hint /plotme deny <name>");
            lore.add("§8Right-click: §7hint /plotme undeny <name>");
            if (plot.getDenied().isEmpty()) {
                lore.add("§7(none)");
            } else if (plot.getDenied().contains("*")) {
                lore.add("§f*");
            } else {
                int shown = 0;
                for (String key : plot.getDenied()) {
                    if (shown >= 5) {
                        lore.add("§8...");
                        break;
                    }
                    lore.add("§f" + resolveName(key));
                    shown++;
                }
            }
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack buildProtectIcon(Plot plot) {
        ItemStack item = new ItemStack(Material.LEVER);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§6Protect: " + (plot.isProtected() ? "§aON" : "§cOFF"));
            List<String> lore = new ArrayList<>();
            lore.add("§8Click to toggle");
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack buildForSaleIcon(Plot plot) {
        ItemStack item = new ItemStack(Material.GOLD_INGOT);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            if (plot.isForSale()) {
                meta.setDisplayName("§eFor sale: §a" + plot.getPrice());
            } else {
                meta.setDisplayName("§eFor sale: §cNo");
            }
            List<String> lore = new ArrayList<>();
            lore.add("§8Click to toggle (uses /plotme sell)");
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack buildHomeIcon(Plot plot) {
        ItemStack item = new ItemStack(Material.NETHER_STAR);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§dTeleport home");
            List<String> lore = new ArrayList<>();
            lore.add("§8Click to /plotme home");
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack buildBiomeButton(BiomeEntry entry, String currentBiome) {
        ItemStack item = new ItemStack(entry.icon);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            boolean active = currentBiome != null && currentBiome.equalsIgnoreCase(entry.key);
            meta.setDisplayName((active ? "§a§l" : "§a") + entry.displayName);
            List<String> lore = new ArrayList<>();
            lore.add("§7Biome key: §f" + entry.key);
            if (active) {
                lore.add("§e(currently set)");
            } else {
                lore.add("§8Click to apply");
            }
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack buildBackButton() {
        ItemStack item = new ItemStack(Material.BARRIER);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§cBack");
            meta.setLore(Collections.singletonList("§8Return to plot menu"));
            item.setItemMeta(meta);
        }
        return item;
    }

    private static String resolveName(String uuidOrWildcard) {
        if ("*".equals(uuidOrWildcard)) {
            return "*";
        }
        try {
            OfflinePlayer op = Bukkit.getOfflinePlayer(UUID.fromString(uuidOrWildcard));
            return op.getName() != null ? op.getName() : uuidOrWildcard;
        } catch (IllegalArgumentException ex) {
            return uuidOrWildcard;
        }
    }
}
