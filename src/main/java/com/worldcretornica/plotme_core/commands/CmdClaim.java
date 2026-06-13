package com.worldcretornica.plotme_core.commands;

import com.worldcretornica.plotme_core.PermissionNames;
import com.worldcretornica.plotme_core.Plot;
import com.worldcretornica.plotme_core.PlotId;
import com.worldcretornica.plotme_core.PlotMapInfo;
import com.worldcretornica.plotme_core.PlotMe_Core;
import com.worldcretornica.plotme_core.api.ICommandSender;
import com.worldcretornica.plotme_core.api.IOfflinePlayer;
import com.worldcretornica.plotme_core.api.IPlayer;
import com.worldcretornica.plotme_core.api.IWorld;
import com.worldcretornica.plotme_core.api.event.PlotCreateEvent;
import net.milkbowl.vault.economy.EconomyResponse;

public class CmdClaim extends PlotCommand {

    public CmdClaim(PlotMe_Core instance) {
        super(instance);
    }

    public String getName() {
        return "claim";
    }

    public boolean execute(ICommandSender sender, String[] args) {
        IPlayer player = (IPlayer) sender;
        if (player.hasPermission(PermissionNames.USER_CLAIM) || player.hasPermission(PermissionNames.ADMIN_CLAIM_OTHER)) {
            IWorld world = player.getWorld();
            PlotMapInfo pmi = manager.getMap(world);
            if (manager.isPlotWorld(world)) {
                // Belt + suspenders against the same registration race that
                // hits CmdAuto: if the plot world is in the PlotMapInfo map
                // but the gen manager hasn't been registered yet, getPlotId
                // would return null (handled below) but downstream
                // isPlotAvailable / createPlot calls would NPE on the
                // missing gen manager. Bail with a friendly message.
                if (manager.getGenManager(world) == null) {
                    player.sendMessage(C("MsgNoPlotWorldSetup"));
                    return true;
                }
                PlotId id = manager.getPlotId(player);

                if (id == null) {
                    player.sendMessage(C("MsgCannotClaimRoad"));
                    return true;
                } else if (!manager.isPlotAvailable(id, world)) {
                    player.sendMessage(C("MsgThisPlotOwned"));
                    return true;
                }

                IOfflinePlayer futurePlotOwner = player;
                if (args.length == 2 && player.hasPermission(PermissionNames.ADMIN_CLAIM_OTHER)) {
                    if (args[1].length() > 16) {
                        player.sendMessage(C("InvalidCommandInput"));
                    }
                    if (serverBridge.getPlayer(args[1]) == null) {
                        player.sendMessage(C("MsgNoPlayerFoundByName"));
                        return true;
                    } else {
                        futurePlotOwner = serverBridge.getPlayer(args[1]);
                    }
                }

                int plotLimit = getPlotLimit(player);

                int plotsOwned = manager.getOwnedPlotCount(player.getUniqueId(), world);

                if (player.getUniqueId().equals(futurePlotOwner.getUniqueId()) && plotLimit != -1 && plotsOwned >= plotLimit) {
                    player.sendMessage(C("MsgAlreadyReachedMaxPlots",plotLimit,plotLimit));
                } else {

                    double price = 0.0;

                    PlotCreateEvent event = new PlotCreateEvent(id, player);

                    if (manager.isEconomyEnabled(pmi)) {
                        price = pmi.getClaimPrice();

                        if (serverBridge.has(player, price)) {
                            plugin.getEventBus().post(event);
                            if (event.isCancelled()) {
                                return true;
                            }
                            EconomyResponse er = serverBridge.withdrawPlayer(player, price);

                            if (!er.transactionSuccess()) {
                                player.sendMessage("§c" + er.errorMessage);
                                serverBridge.getLogger().warning(er.errorMessage);
                                return true;
                            }
                        } else {
                            player.sendMessage(
                                    C("MsgNotEnoughBuy") + " " + C("WordMissing") + " §b" + serverBridge.getEconomy().get().format(price) + "§r");
                            return true;
                        }
                    } else {
                        plugin.getEventBus().post(event);
                    }

                    if (!event.isCancelled()) {
                        Plot plot = manager.createPlot(id, world, player.getName(), player.getUniqueId(), pmi);

                        // Auto-link on claim is intentionally disabled. Merging is manual
                        // only: the owner must run /plotme merge <direction> explicitly.
                        // The previous AutoLinkPlots-driven adjustLinkedPlots call has been
                        // removed so /plotme claim never folds neighbouring plots into a
                        // cluster on its own. CmdMerge remains the supported path.
                        if (player.getUniqueId().equals(futurePlotOwner.getUniqueId())) {
                            player.sendMessage(
                                    C("MsgThisPlotYours") + " " + C("WordUse") + " §b/plotme home §r" + C("MsgToGetToIt"));
                        } else {
                            player.sendMessage(C("MsgThisPlotIsNow") + " §b" + player.getName() + "§r. " + C("WordUse")
                                    + " §b/plotme home §r" + C("MsgToGetToIt"));
                        }
                        // Audible confirmation on successful claim.
                        player.playSound("ENTITY_PLAYER_LEVELUP", 0.8f, 1.4f);

                        if (isAdvancedLogging()) {
                            if (price == 0) {
                                serverBridge.getLogger().info(player.getName() + " " + C("MsgClaimedPlot") + " " + id);
                            } else {
                                serverBridge.getLogger()
                                        .info(player.getName() + " " + C("MsgClaimedPlot") + " " + id + (" " + C("WordFor") + " " + price));
                            }
                        }
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
        return C("CmdClaimUsage");
    }

}
