package com.worldcretornica.plotme_core.commands;

import com.worldcretornica.plotme_core.PermissionNames;
import com.worldcretornica.plotme_core.Plot;
import com.worldcretornica.plotme_core.PlotMapInfo;
import com.worldcretornica.plotme_core.PlotMe_Core;
import com.worldcretornica.plotme_core.api.ICommandSender;
import com.worldcretornica.plotme_core.api.IPlayer;
import com.worldcretornica.plotme_core.utils.ListPartition;

import java.util.List;

public class CmdExpired extends PlotCommand {

    public CmdExpired(PlotMe_Core instance) {
        super(instance);
    }

    public String getName() {
        return "expired";
    }

    public boolean execute(ICommandSender sender, String[] args) {
        IPlayer player = (IPlayer) sender;
        if (player.hasPermission(PermissionNames.ADMIN_EXPIRED)) {
            if (manager.isPlotWorld(player.getWorld())) {
                PlotMapInfo pmi = manager.getMap(player);
                if (pmi.getDaysToExpiration() != 0) {
                    int page = 1;

                    if (args.length == 2) {
                        page = Integer.parseInt(args[1]);
                    }
                    List<List<Plot>> partition = ListPartition.partition(plugin.getSqlManager().getExpiredPlots(player.getWorld()), 10);
                    if (partition.isEmpty()) {
                        player.sendMessage(C("MsgNoPlotExpired"));
                    } else {
                        player.sendMessage(C("MsgExpiredPlotsPage", page, partition.size()));
                        for (Plot plot : partition.get(page - 1)) {
                            assert plot.getExpiredDate() != null;
                            player.sendMessage("§b" + plot.getId() + "§7 -> §f" + plot.getOwner() + "§7 @ §e" + plot.getExpiredDate().toString() + "§r");
                        }
                    }
                } else {
                    return true;
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
        return C("CmdExpiredUsage");
    }
}