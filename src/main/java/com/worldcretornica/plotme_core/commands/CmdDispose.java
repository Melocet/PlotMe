package com.worldcretornica.plotme_core.commands;

import com.worldcretornica.plotme_core.PermissionNames;
import com.worldcretornica.plotme_core.Plot;
import com.worldcretornica.plotme_core.PlotMapInfo;
import com.worldcretornica.plotme_core.PlotMe_Core;
import com.worldcretornica.plotme_core.api.ICommandSender;
import com.worldcretornica.plotme_core.api.IPlayer;
import com.worldcretornica.plotme_core.api.IWorld;
import com.worldcretornica.plotme_core.api.event.PlotDisposeEvent;
import net.milkbowl.vault.economy.EconomyResponse;

public class CmdDispose extends PlotCommand {

    public CmdDispose(PlotMe_Core instance) {
        super(instance);
    }

    public String getName() {
        return "dispose";
    }

    public boolean execute(ICommandSender sender, String[] args) {
        if (args.length > 1) {
            sender.sendMessage(getUsage());
            return true;
        }
        IPlayer player = (IPlayer) sender;
        if (player.hasPermission(PermissionNames.ADMIN_DISPOSE) || player.hasPermission(PermissionNames.USER_DISPOSE)) {
            IWorld world = player.getWorld();
            PlotMapInfo pmi = manager.getMap(world);
            if (manager.isPlotWorld(world)) {
                Plot plot = manager.getPlot(player);

                if (plot != null) {
                    if (plot.isProtected()) {
                        player.sendMessage(C("MsgPlotProtectedNotDisposed"));
                    } else if (player.getUniqueId().equals(plot.getOwnerId()) || player.hasPermission(PermissionNames.ADMIN_DISPOSE)) {

                        double cost = pmi.getDisposePrice();

                        PlotDisposeEvent event = new PlotDisposeEvent(plot, player);

                        if (manager.isEconomyEnabled(pmi)) {
                            if (!serverBridge.has(player, cost)) {
                                player.sendMessage(C("MsgNotEnoughDispose"));
                                return true;
                            }

                            plugin.getEventBus().post(event);

                            if (event.isCancelled()) {
                                return true;
                            }
                            EconomyResponse economyResponse = serverBridge.withdrawPlayer(player, cost);

                            if (!economyResponse.transactionSuccess()) {
                                player.sendMessage("§c" + economyResponse.errorMessage);
                                plugin.getLogger().warning(economyResponse.errorMessage);
                                return true;
                            }
                        } else {
                            plugin.getEventBus().post(event);
                        }

                        if (!event.isCancelled()) {
                            // If the plot was part of a merged cluster we need to
                            // rebuild the road geometry that fillRoad / fillCenterIntersection
                            // had carved through, BEFORE the plot's row is gone. For
                            // an un-merged plot disposeMergedPlot is a no-op.
                            boolean wasMerged = !plot.getMergedWith().isEmpty();
                            manager.disposeMergedPlot(plot);
                            manager.adjustWall(plot, false);
                            if (manager.deletePlot(plot)) {
                                if (wasMerged) {
                                    player.sendMessage(C("MsgPlotDisposed"));
                                } else {
                                    player.sendMessage(C("PlotDisposed"));
                                }
                            } else {
                                player.sendMessage("§cPlot was not disposed? Something stopped this command.");
                            }

                            if (isAdvancedLogging()) {
                                plugin.getLogger().info(player.getName() + " " + C("MsgDisposedPlot") + " " + plot.getId());
                            }
                        }
                    } else {
                        player.sendMessage(C("MsgThisPlot") + "§7(§b" + plot.getId() + "§7) §r" + C("MsgNotYoursCannotDispose"));
                    }
                }
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
        return C("CmdDisposeUsage");
    }
}
