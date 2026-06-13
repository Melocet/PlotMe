package com.worldcretornica.plotme_core.commands;

import com.worldcretornica.plotme_core.PermissionNames;
import com.worldcretornica.plotme_core.PlotId;
import com.worldcretornica.plotme_core.PlotMapInfo;
import com.worldcretornica.plotme_core.PlotMe_Core;
import com.worldcretornica.plotme_core.api.ICommandSender;
import com.worldcretornica.plotme_core.api.IPlayer;
import com.worldcretornica.plotme_core.api.IWorld;
import net.milkbowl.vault.economy.EconomyResponse;

import java.util.UUID;

public class CmdAuto extends PlotCommand {

    public CmdAuto(PlotMe_Core instance) {
        super(instance);
    }

    public String getName() {
        return "auto";
    }

    public boolean execute(ICommandSender sender, String[] args) {
        final IPlayer player = (IPlayer) sender;
        if (player.hasPermission(PermissionNames.USER_AUTO)) {
            if (manager.isPlotWorld(player) || plugin.getConfig().getBoolean("allowWorldTeleport")) {
                final IWorld world;
                if (plugin.getConfig().getBoolean("allowWorldTeleport") && args.length == 2) {
                    world = manager.getWorld(args[1]);
                    if (world == null) {
                        player.sendMessage(C("NotPlotWorld"));
                        return true;
                    }
                    if (!manager.isPlotWorld(world)) {
                        player.sendMessage(C("NotPlotWorld"));
                        return true;
                    }
                } else {
                    world = player.getWorld();
                }

                // Guard the spiral search against a partially-loaded plotworld.
                // isPlotWorld(world) above only checks the PlotMapInfo map; the
                // gen-manager map is populated separately (WorldInitEvent /
                // WorldLoadEvent). Right after `/mv create plots normal -g
                // PlotMe`, there is a window where the world is "a plot world"
                // per the config / PlotMapInfo but no gen manager has been
                // registered yet, which causes an NPE inside the async
                // isPlotAvailable -> getPlotTopLoc path. Bail early with a
                // friendly message instead of crashing.
                if (manager.getGenManager(world) == null) {
                    player.sendMessage(C("MsgNoPlotWorldSetup"));
                    return true;
                }

                int playerLimit = getPlotLimit(player);

                int plotsOwned = manager.getOwnedPlotCount(player.getUniqueId(), world);

                if (playerLimit != -1 && plotsOwned >= playerLimit && !player.hasPermission("PlotMe.admin")) {
                    player.sendMessage(C("MsgAlreadyReachedMaxPlots", plotsOwned,playerLimit));
                    return true;
                }
                final PlotMapInfo pmi = manager.getMap(world);
                final int spiralMax = Math.max(1, plugin.getConfig().getInt("autoSpiralMax", 50));
                serverBridge.runTaskAsynchronously(new Runnable() {
                    @Override public void run() {
                        loop:
                        for (int i = 0; i <= spiralMax; i++) {
                            for (int x = -i; x <= i; x++) {
                                for (int z = -i; z <= i; z++) {
                                    final PlotId id = new PlotId(x, z);

                                    if (manager.isPlotAvailable(id, world)) {
                                        final String name = player.getName();
                                        final UUID uuid = player.getUniqueId();


                                        if (manager.isEconomyEnabled(world)) {
                                            double price = pmi.getClaimPrice();

                                            if (serverBridge.has(player, price)) {
                                                EconomyResponse er = serverBridge.withdrawPlayer(player, price);

                                                if (!er.transactionSuccess()) {
                                                    player.sendMessage("§c" + er.errorMessage);
                                                    return;
                                                }
                                            } else {
                                                player.sendMessage("§cYou do not have enough money to buy this plot");
                                                return;
                                            }
                                        }
                                        plugin.getServerBridge().runTask(new Runnable() {
                                            @Override public void run() {
                                                manager.createPlot(id, world, name, uuid, pmi);
                                                // Teleport must run on the main thread — Paper rejects
                                                // PlayerTeleportEvent from async scheduler workers.
                                                // getPlotHome can now return null when the gen manager
                                                // is unregistered between the spiral check and this
                                                // sync hop (e.g. world unload race). Skip teleport in
                                                // that pathological case so we don't NPE on a freshly
                                                // claimed plot.
                                                com.worldcretornica.plotme_core.api.Location home =
                                                        manager.getPlotHome(id, world);
                                                if (home != null) {
                                                    player.teleport(home, plugin);
                                                }
                                                player.sendMessage(C("MsgThisPlotYours") + " " + C("WordUse") + " §b/plotme home§r" + " " + C("MsgToGetToIt"));
                                            }
                                        });
                                        break loop;
                                    }
                                }
                            }
                        }
                    }
                });
            } else {
                player.sendMessage(C("NotPlotWorld"));
            }
        } else {
            return false;
        }
        return true;
    }

    @Override
    public String getUsage() {
        return C("CmdAutoUsage");
    }

}