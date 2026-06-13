package com.worldcretornica.plotme_core.commands;

import com.worldcretornica.plotme_core.PermissionNames;
import com.worldcretornica.plotme_core.Plot;
import com.worldcretornica.plotme_core.PlotMe_Core;
import com.worldcretornica.plotme_core.api.ICommandSender;
import com.worldcretornica.plotme_core.api.IPlayer;
import com.worldcretornica.plotme_core.api.IWorld;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class CmdInfo extends PlotCommand {

    public CmdInfo(PlotMe_Core instance) {
        super(instance);
    }

    @Override
    public List<String> getAliases() {
        return Collections.singletonList("i");
    }

    public String getName() {
        return "info";
    }

    public boolean execute(ICommandSender sender, String[] args) {
        if (args.length > 1) {
            sender.sendMessage(getUsage());
            return true;
        }
        IPlayer player = (IPlayer) sender;
        if (player.hasPermission(PermissionNames.USER_INFO)) {
            IWorld world = player.getWorld();
            if (manager.isPlotWorld(world)) {
                // Use the merged-road-aware lookup so /plot info also works
                // when standing on a road strip that was filled in by a
                // merge. Falls back to the plain on-plot lookup if the
                // player isn't on a road, and returns null on a real
                // unclaimed road (so the "NoPlotFound" message still fires).
                Plot plot = manager.getPlotOrMergedRoad(player);

                if (plot == null) {
                    player.sendMessage(C("NoPlotFound"));
                    return true;
                }
                player.sendMessage("§eInternal ID: §b" + plot.getInternalID() + "§r");
                player.sendMessage(
                        "§eID: §b" + plot.getId().getID() + "§r " + C("InfoOwner", serverBridge.getOfflinePlayer(plot.getOwnerId()).getName()) + " " + C
                                ("InfoBiome", plot
                                        .getBiome()));
                player.sendMessage("§eLikes: §b" + plot.getLikes() + "§r");
                player.sendMessage("§eCreated: §b" + plot.getCreatedDate() + "§r");
                final String neverExpire = C("InfoExpire") + ": " + C("WordNever");
                if (plot.getExpiredDate() == null) {
                    if (plot.isFinished()) {
                        if (plot.isProtected()) {
                            player.sendMessage(neverExpire
                                    + " " + C("InfoFinished") + ": " + C("WordYes")
                                    + " " + C("InfoProtected") + ": " + C("WordYes"));
                        } else {
                            player.sendMessage(neverExpire
                                    + " " + C("InfoFinished") + ": " + C("WordYes")
                                    + " " + C("InfoProtected") + ": " + C("WordNo"));
                        }
                    } else {
                        if (plot.isProtected()) {
                            player.sendMessage(neverExpire
                                    + " " + C("InfoFinished") + ": " + C("WordNo")
                                    + " " + C("InfoProtected") + ": " + C("WordYes"));
                        } else {
                            player.sendMessage(neverExpire
                                    + " " + C("InfoFinished") + ": " + C("WordNo")
                                    + " " + C("InfoProtected") + ": " + C("WordNo"));
                        }
                    }
                } else if (plot.isProtected()) {
                    if (plot.isFinished()) {
                        player.sendMessage(neverExpire
                                + " " + C("InfoFinished") + ": " + C("WordYes")
                                + " " + C("InfoProtected") + ": " + C("WordYes"));
                    } else {
                        player.sendMessage(neverExpire
                                + " " + C("InfoFinished") + ": " + C("WordNo")
                                + " " + C("InfoProtected") + ": " + C("WordYes"));
                    }
                } else if (plot.isFinished()) {
                    player.sendMessage(C("InfoExpire") + ": §f" + plot.getExpiredDate() + "§r"
                            + " " + C("InfoFinished") + ": " + C("WordYes")
                            + " " + C("InfoProtected") + ": " + C("WordNo"));
                } else {
                    player.sendMessage(C("InfoExpire") + ": §f" + plot.getExpiredDate() + "§r"
                            + " " + C("InfoFinished") + ": " + C("WordNo")
                            + " " + C("InfoProtected") + ": " + C("WordNo"));
                }

                if (!plot.getMembers().isEmpty()) {
                    StringBuilder builder = new StringBuilder("§bMembers: §f");
                    if (!plot.getMembers().containsKey("*")) {
                        for (Map.Entry<String, Plot.AccessLevel> member : plot.getMembers().entrySet()) {
                            builder.append(plugin.getServerBridge().getOfflinePlayer(UUID.fromString(member.getKey())).getName()).append(" (")
                                    .append(member.getValue().toString()).append(")   ");
                        }
                    } else {
                        builder.append("*");
                    }
                    player.sendMessage(builder.toString());
                }

                if (!plot.getDenied().isEmpty()) {
                    StringBuilder builder = new StringBuilder(C("InfoDenied"));
                    builder.append(": ");
                    if (!plot.getDenied().contains("*")) {
                        for (String s : plot.getDenied()) {
                            builder.append(plugin.getServerBridge().getOfflinePlayer(UUID.fromString(s)).getName()).append("  ");
                        }
                    } else {
                        builder.append('*');
                    }

                    player.sendMessage(builder.toString());
                }

                if (manager.isEconomyEnabled(world)) {
                    if (plot.isForSale()) {
                        player.sendMessage(C("InfoForSale") + ": §a" + plot.getPrice() + "§r");
                    } else {
                        player.sendMessage(C("InfoForSale") + ": " + C("WordNo"));
                    }
                }

                player.sendMessage(C("WordBottom") + ": §f" + plot.getPlotBottomLoc().toString() + "§r");
                player.sendMessage(C("WordTop") + ": §f" + plot.getPlotTopLoc().toString() + "§r");

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
        return C("CmdInfoUsage");
    }
}
