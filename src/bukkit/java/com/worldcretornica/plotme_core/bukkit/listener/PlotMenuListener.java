package com.worldcretornica.plotme_core.bukkit.listener;

import com.worldcretornica.plotme_core.PermissionNames;
import com.worldcretornica.plotme_core.Plot;
import com.worldcretornica.plotme_core.PlotMapInfo;
import com.worldcretornica.plotme_core.PlotMeCoreManager;
import com.worldcretornica.plotme_core.PlotMe_Core;
import com.worldcretornica.plotme_core.api.IServerBridge;
import com.worldcretornica.plotme_core.api.IWorld;
import com.worldcretornica.plotme_core.api.event.PlotBiomeChangeEvent;
import com.worldcretornica.plotme_core.api.event.PlotProtectChangeEvent;
import com.worldcretornica.plotme_core.api.event.PlotSellChangeEvent;
import com.worldcretornica.plotme_core.bukkit.PlotMe_CorePlugin;
import com.worldcretornica.plotme_core.bukkit.gui.PlotMenuGui;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.Optional;

/**
 * Routes clicks inside the {@link PlotMenuGui} to the underlying plot
 * operations. We avoid going through the Cmd*.execute() codepath because that
 * would require synthesising args[] and re-doing permission checks; instead
 * we mutate the Plot directly (same calls the commands make) and rebuild
 * the GUI in place.
 *
 * Each button click plays a short UI click sound so the audio chirp the
 * user already approves of stays consistent across pages.
 */
public class PlotMenuListener implements Listener {

    private static final String CLICK_SOUND = "UI_BUTTON_CLICK";
    private static final float CLICK_VOL = 0.6f;
    private static final float CLICK_PITCH = 1.4f;

    private final PlotMe_CorePlugin plugin;

    public PlotMenuListener(PlotMe_CorePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        Inventory top = event.getInventory();
        InventoryHolder holder = top.getHolder();
        if (!(holder instanceof PlotMenuGui)) {
            return;
        }
        PlotMenuGui gui = (PlotMenuGui) holder;
        if (event.getClickedInventory() != top) {
            // Clicks in the player's own inventory — block shift-move into menu.
            if (event.getClick() == ClickType.SHIFT_LEFT || event.getClick() == ClickType.SHIFT_RIGHT) {
                event.setCancelled(true);
            }
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }
        Player bukkitPlayer = (Player) event.getWhoClicked();
        if (!bukkitPlayer.getUniqueId().equals(gui.getViewer())) {
            return;
        }

        Plot plot = gui.resolvePlot();
        if (plot == null) {
            bukkitPlayer.sendMessage("§cPlot no longer exists.");
            bukkitPlayer.closeInventory();
            return;
        }

        if (gui.getPage() == PlotMenuGui.Page.BIOMES) {
            handleBiomePageClick(event, gui, plot, bukkitPlayer);
            return;
        }

        handleMainPageClick(event, gui, plot, bukkitPlayer);
    }

    // ---------- main page dispatch ----------

    private void handleMainPageClick(InventoryClickEvent event, PlotMenuGui gui, Plot plot, Player bukkitPlayer) {
        boolean isOwner = bukkitPlayer.getUniqueId().equals(plot.getOwnerId());
        boolean isAdmin = bukkitPlayer.hasPermission("plotme.admin");

        switch (event.getSlot()) {
            case PlotMenuGui.SLOT_HOME:
                playClick(bukkitPlayer);
                handleHome(bukkitPlayer);
                return;
            case PlotMenuGui.SLOT_PROTECT:
                if (!isOwner && !isAdmin) {
                    deny(bukkitPlayer);
                    return;
                }
                playClick(bukkitPlayer);
                toggleProtect(bukkitPlayer, plot);
                break;
            case PlotMenuGui.SLOT_FORSALE:
                if (!isOwner && !isAdmin) {
                    deny(bukkitPlayer);
                    return;
                }
                playClick(bukkitPlayer);
                toggleForSale(bukkitPlayer, plot);
                break;
            case PlotMenuGui.SLOT_MEMBERS:
                playClick(bukkitPlayer);
                bukkitPlayer.sendMessage(event.getClick().isRightClick()
                        ? "§7Type §f/plotme remove <name>§7 to remove a member."
                        : "§7Type §f/plotme add <name>§7 to add a member.");
                break;
            case PlotMenuGui.SLOT_DENIED:
                playClick(bukkitPlayer);
                bukkitPlayer.sendMessage(event.getClick().isRightClick()
                        ? "§7Type §f/plotme undeny <name>§7 to un-deny."
                        : "§7Type §f/plotme deny <name>§7 to deny.");
                break;
            case PlotMenuGui.SLOT_BIOME:
                // Open the biome page rather than printing a hint — players
                // can now pick a biome from a curated icon list.
                playClick(bukkitPlayer);
                gui.showBiomePage(plot);
                return;
            case PlotMenuGui.SLOT_OWNER:
            default:
                return;
        }

        // Re-resolve and rebuild after a mutation.
        Plot updated = gui.resolvePlot();
        if (updated != null) {
            gui.rebuild(updated);
        }
    }

    // ---------- biome page dispatch ----------

    private void handleBiomePageClick(InventoryClickEvent event, PlotMenuGui gui, Plot plot, Player bukkitPlayer) {
        int slot = event.getSlot();
        if (slot == PlotMenuGui.SLOT_BIOME_BACK) {
            playClick(bukkitPlayer);
            gui.showMainPage(plot);
            return;
        }
        PlotMenuGui.BiomeEntry entry = gui.getBiomeAt(slot);
        if (entry == null) {
            return;
        }
        playClick(bukkitPlayer);
        applyBiome(bukkitPlayer, plot, entry.key);
        // Re-resolve and rebuild so the "currently set" highlight updates.
        Plot updated = gui.resolvePlot();
        if (updated != null) {
            gui.rebuild(updated);
        }
    }

    // ---------- handlers ----------

    private void handleHome(Player bukkitPlayer) {
        bukkitPlayer.closeInventory();
        bukkitPlayer.performCommand("plotme home");
    }

    private void toggleProtect(Player bukkitPlayer, Plot plot) {
        if (!bukkitPlayer.hasPermission(PermissionNames.USER_PROTECT)
                && !bukkitPlayer.hasPermission(PermissionNames.ADMIN_PROTECT)) {
            deny(bukkitPlayer);
            return;
        }
        PlotMe_Core api = plugin.getAPI();
        PlotMeCoreManager mgr = PlotMeCoreManager.getInstance();
        IWorld world = mgr.getWorld(plot.getWorld().getName());
        if (world == null) {
            return;
        }
        com.worldcretornica.plotme_core.api.IPlayer wrapped = plugin.wrapPlayer(bukkitPlayer);
        boolean target = !plot.isProtected();
        PlotProtectChangeEvent ev = new PlotProtectChangeEvent(plot, wrapped, target);
        api.getEventBus().post(ev);
        if (ev.isCancelled()) {
            return;
        }
        plot.setProtected(target);
        mgr.adjustWall(wrapped);
        api.getSqlManager().savePlot(plot);
        bukkitPlayer.sendMessage(target ? "§aPlot protected." : "§ePlot no longer protected.");
    }

    private void toggleForSale(Player bukkitPlayer, Plot plot) {
        if (!bukkitPlayer.hasPermission(PermissionNames.USER_SELL)
                && !bukkitPlayer.hasPermission(PermissionNames.ADMIN_SELL)) {
            deny(bukkitPlayer);
            return;
        }
        PlotMe_Core api = plugin.getAPI();
        PlotMeCoreManager mgr = PlotMeCoreManager.getInstance();
        IWorld world = mgr.getWorld(plot.getWorld().getName());
        if (world == null) {
            return;
        }
        PlotMapInfo pmi = mgr.getMap(world);
        if (!mgr.isEconomyEnabled(pmi)) {
            bukkitPlayer.sendMessage("§cEconomy is disabled in this world.");
            return;
        }
        if (!pmi.isCanPutOnSale()) {
            bukkitPlayer.sendMessage("§cSelling plots is disabled here.");
            return;
        }
        com.worldcretornica.plotme_core.api.IPlayer wrapped = plugin.wrapPlayer(bukkitPlayer);
        if (plot.isForSale()) {
            PlotSellChangeEvent ev = new PlotSellChangeEvent(plot, wrapped, plot.getPrice(), false);
            api.getEventBus().post(ev);
            if (ev.isCancelled()) {
                return;
            }
            plot.setPrice(0.0);
            plot.setForSale(false);
            api.getSqlManager().savePlot(plot);
            mgr.adjustWall(wrapped);
            mgr.removeSellSign(plot);
            bukkitPlayer.sendMessage("§ePlot no longer for sale.");
        } else {
            double price = pmi.getSellToPlayerPrice();
            PlotSellChangeEvent ev = new PlotSellChangeEvent(plot, wrapped, price, true);
            api.getEventBus().post(ev);
            if (ev.isCancelled()) {
                return;
            }
            plot.setPrice(price);
            plot.setForSale(true);
            api.getSqlManager().savePlot(plot);
            mgr.getGenManager(world).adjustPlotFor(plot, true, plot.isProtected(), plot.isForSale());
            mgr.setSellSign(plot);
            bukkitPlayer.sendMessage("§aPlot is now for sale at §f" + price + "§a.");
        }
    }

    /**
     * Mirrors {@link com.worldcretornica.plotme_core.commands.CmdBiome#execute}:
     * validates the biome key against the runtime registry via the server
     * bridge (so the GUI accepts the same set the command does), enforces the
     * same permission + ownership + economy gates, fires
     * {@link PlotBiomeChangeEvent}, persists the change, and sends the same
     * {@code BiomeChanged} caption the command flow sends. Any divergence
     * here would cause the GUI and the command to produce different output —
     * the spec explicitly forbids that.
     */
    private void applyBiome(Player bukkitPlayer, Plot plot, String biomeKey) {
        com.worldcretornica.plotme_core.api.IPlayer wrapped = plugin.wrapPlayer(bukkitPlayer);

        if (!wrapped.hasPermission(PermissionNames.USER_BIOME)) {
            deny(bukkitPlayer);
            return;
        }

        PlotMe_Core api = plugin.getAPI();
        PlotMeCoreManager mgr = PlotMeCoreManager.getInstance();
        IWorld world = mgr.getWorld(plot.getWorld().getName());
        if (world == null) {
            return;
        }
        PlotMapInfo pmi = mgr.getMap(world);
        IServerBridge bridge = api.getServerBridge();

        // Same validation path the command uses — any biome unknown to the
        // running server's registry is rejected with the same caption.
        Optional<String> biome = bridge.getBiome(biomeKey);
        if (!biome.isPresent()) {
            wrapped.sendMessage(api.C("InvalidBiome", biomeKey));
            return;
        }

        if (!bukkitPlayer.getUniqueId().equals(plot.getOwnerId())) {
            wrapped.sendMessage(api.C("MsgThisPlot") + "§7(§b" + plot.getId() + "§7) §r" + api.C("MsgNotYoursNotAllowedBiome"));
            return;
        }

        double price = 0.0;
        PlotBiomeChangeEvent event = new PlotBiomeChangeEvent(plot, wrapped, biome.get());
        api.getEventBus().post(event);

        if (mgr.isEconomyEnabled(pmi)) {
            price = pmi.getBiomeChangePrice();
            if (!bridge.has(wrapped, price)) {
                wrapped.sendMessage("§eIt costs §b" + bridge.getEconomy().get().format(price) + "§e to change the biome.");
                return;
            }
            if (event.isCancelled()) {
                return;
            }
            EconomyResponse er = bridge.withdrawPlayer(wrapped, price);
            if (!er.transactionSuccess()) {
                wrapped.sendMessage("§c" + er.errorMessage);
                bridge.getLogger().warning(er.errorMessage);
                return;
            }
        }

        if (event.isCancelled()) {
            return;
        }

        plot.setBiome(biome.get());
        int blocks = mgr.setBiome(plot);
        api.getSqlManager().savePlot(plot);
        // Same caption the command path uses — the GUI and /plotme biome must
        // produce the same confirmation, including the block count, so the
        // player sees the change covered the whole plot (or full merged
        // cluster) and not just the cell under their feet.
        wrapped.sendMessage(api.C("MsgBiomeApplied", biome.get(), String.valueOf(blocks)));

        if (api.getConfig().getBoolean("AdvancedLogging")) {
            String playerName = bukkitPlayer.getName();
            if (price == 0) {
                bridge.getLogger().info(playerName + " " + api.C("MsgChangedBiome") + " " + plot.getId() + " "
                        + api.C("WordTo") + " " + biome.get());
            } else {
                bridge.getLogger().info(playerName + " " + api.C("MsgChangedBiome") + " " + plot.getId() + " "
                        + api.C("WordTo") + " " + biome.get() + (" " + api.C("WordFor") + " " + price));
            }
        }
    }

    private void deny(Player bukkitPlayer) {
        bukkitPlayer.sendMessage("§cYou do not own this plot.");
    }

    private void playClick(Player bukkitPlayer) {
        try {
            bukkitPlayer.playSound(bukkitPlayer.getLocation(),
                    CLICK_SOUND.toLowerCase().replace('_', '.'),
                    CLICK_VOL, CLICK_PITCH);
        } catch (Throwable ignored) {
            // Sound names vary across server forks/versions — never let an
            // audio failure break the menu interaction.
        }
    }
}
