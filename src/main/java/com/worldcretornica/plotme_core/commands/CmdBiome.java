package com.worldcretornica.plotme_core.commands;

import com.worldcretornica.plotme_core.PermissionNames;
import com.worldcretornica.plotme_core.Plot;
import com.worldcretornica.plotme_core.PlotMapInfo;
import com.worldcretornica.plotme_core.PlotMe_Core;
import com.worldcretornica.plotme_core.api.ICommandSender;
import com.worldcretornica.plotme_core.api.IPlayer;
import com.worldcretornica.plotme_core.api.IWorld;
import com.worldcretornica.plotme_core.api.event.PlotBiomeChangeEvent;
import net.milkbowl.vault.economy.EconomyResponse;

import java.util.Optional;

public class CmdBiome extends PlotCommand {

    public CmdBiome(PlotMe_Core instance) {
        super(instance);
    }

    public String getName() {
        return "biome";
    }

    public boolean execute(ICommandSender sender, String[] args) {
        if (args.length > 4) {
            sender.sendMessage(getUsage());
            return true;
        }

        IPlayer player = (IPlayer) sender;
        if (player.hasPermission(PermissionNames.USER_BIOME)) {
            IWorld world = player.getWorld();
            PlotMapInfo pmi = manager.getMap(world);
            if (manager.isPlotWorld(world)) {
                Plot plot = manager.getPlot(player);
                if (plot != null) {
                    Optional<String> biome = Optional.empty();
                    String biomeInput = "";
                    if (args.length == 2) {
                        biomeInput = args[1];
                        biome = serverBridge.getBiome(biomeInput);
                    } else if (args.length == 3) {
                        biomeInput = args[1] + " " + args[2];
                        biome = serverBridge.getBiome(biomeInput);
                    } else if (args.length == 4) {
                        biomeInput = args[1] + " " + args[2] + " " + args[3];
                        biome = serverBridge.getBiome(biomeInput);
                    }
                    if (!biome.isPresent()) {
                        player.sendMessage(C("InvalidBiome", biomeInput));
                        return true;
                    }

                    String playerName = player.getName();

                    if (player.getUniqueId().equals(plot.getOwnerId())) {

                        double price = 0.0;

                        PlotBiomeChangeEvent event = new PlotBiomeChangeEvent(plot, player, biome.get());
                        plugin.getEventBus().post(event);

                        if (manager.isEconomyEnabled(pmi)) {
                            price = pmi.getBiomeChangePrice();

                            if (!serverBridge.has(player, price)) {
                                player.sendMessage("§eIt costs §b" + serverBridge.getEconomy().get().format(price) + "§e to change the biome.");
                                return true;
                            } else if (!event.isCancelled()) {
                                EconomyResponse er = serverBridge.withdrawPlayer(player, price);

                                if (!er.transactionSuccess()) {
                                    player.sendMessage("§c" + er.errorMessage);
                                    serverBridge.getLogger().warning(er.errorMessage);
                                    return true;
                                }
                            } else {
                                return true;
                            }
                        }

                        if (!event.isCancelled()) {
                            plot.setBiome(biome.get());
                            int blocks = manager.setBiome(plot);
                            plugin.getSqlManager().savePlot(plot);

                            // MsgBiomeApplied reports the full block count so
                            // the player can see the change actually covered
                            // the whole plot (and the entire merged cluster
                            // when applicable), not just where they were
                            // standing — that was the bug this caption was
                            // introduced to make visible.
                            player.sendMessage(C("MsgBiomeApplied", biome.get(), String.valueOf(blocks)));

                            if (isAdvancedLogging()) {
                                if (price == 0) {
                                    serverBridge.getLogger()
                                            .info(playerName + " " + C("MsgChangedBiome") + " " + plot.getId() + " " + C("WordTo") + " "
                                                    + biome.get());
                                } else {
                                    serverBridge.getLogger()
                                            .info(playerName + " " + C("MsgChangedBiome") + " " + plot.getId() + " " + C("WordTo") + " "
                                                    + biome.get() + (" " + C("WordFor") + " " + price));
                                }
                            }
                        }
                    } else {
                        player.sendMessage(C("MsgThisPlot") + "§7(§b" + plot.getId() + "§7) §r" + C("MsgNotYoursNotAllowedBiome"));
                    }
                } else {
                    player.sendMessage(C("MsgThisPlot") + C("MsgHasNoOwner"));
                }
            } else {
                player.sendMessage(C("NotPlotWorld"));
                return true;
            }
        } else {
            return false;
        }
        return true;
    }

    @Override
    public String getUsage() {
        return C("CmdBiomeUsage");
    }

}