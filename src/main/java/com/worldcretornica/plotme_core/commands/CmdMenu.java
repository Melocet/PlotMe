package com.worldcretornica.plotme_core.commands;

import com.worldcretornica.plotme_core.Plot;
import com.worldcretornica.plotme_core.PlotMe_Core;
import com.worldcretornica.plotme_core.api.ICommandSender;
import com.worldcretornica.plotme_core.api.IPlayer;
import com.worldcretornica.plotme_core.api.IWorld;
import com.worldcretornica.plotme_core.bukkit.api.BukkitPlayer;
import com.worldcretornica.plotme_core.bukkit.gui.PlotMenuGui;

import java.util.Collections;
import java.util.List;

/**
 * /plotme menu — opens a chest GUI for the plot the player is standing on.
 * The actual GUI is Bukkit-only; this command lives in the platform-neutral
 * package because the dispatch table is wired up here, but it only fires for
 * Bukkit-backed players (which is the only platform in this fork — Sponge
 * support has been dropped, see pom.xml).
 */
public class CmdMenu extends PlotCommand {

    public static final String PERMISSION = "plotme.use.menu";

    public CmdMenu(PlotMe_Core instance) {
        super(instance);
    }

    @Override
    public String getName() {
        return "menu";
    }

    @Override
    public List<String> getAliases() {
        return Collections.singletonList("gui");
    }

    @Override
    public boolean execute(ICommandSender sender, String[] args) {
        if (!(sender instanceof IPlayer)) {
            sender.sendMessage("§cOnly players can open the plot menu.");
            return true;
        }
        IPlayer player = (IPlayer) sender;
        if (!player.hasPermission(PERMISSION)) {
            return false;
        }
        IWorld world = player.getWorld();
        if (!manager.isPlotWorld(world)) {
            player.sendMessage(C("NotPlotWorld"));
            return true;
        }
        Plot plot = manager.getPlot(player);
        if (plot == null) {
            player.sendMessage(C("NoPlotFound"));
            return true;
        }
        // Owners can always open. Trusted members can open. Admins can open.
        boolean isOwner = player.getUniqueId().equals(plot.getOwnerId());
        boolean isMember = plot.isMember(player.getUniqueId()).isPresent();
        boolean isAdmin = player.hasPermission("plotme.admin");
        if (!isOwner && !isMember && !isAdmin) {
            player.sendMessage(C("MsgDoNotOwnPlot"));
            return true;
        }

        if (!(player instanceof BukkitPlayer)) {
            player.sendMessage("§cMenu is only available on Bukkit/Paper.");
            return true;
        }
        BukkitPlayer bp = (BukkitPlayer) player;
        PlotMenuGui gui = new PlotMenuGui(bp.getPlayer(), plot);
        bp.getPlayer().openInventory(gui.getInventory());
        // The inventory's holder IS the PlotMenuGui — the click listener
        // recovers it via InventoryHolder.
        return true;
    }

    @Override
    public String getUsage() {
        return "/plotme menu";
    }
}
