package com.worldcretornica.plotme_core.commands;

import com.worldcretornica.plotme_core.PermissionNames;
import com.worldcretornica.plotme_core.Plot;
import com.worldcretornica.plotme_core.PlotMe_Core;
import com.worldcretornica.plotme_core.api.ICommandSender;
import com.worldcretornica.plotme_core.api.IPlayer;
import com.worldcretornica.plotme_core.utils.ListPartition;

import java.util.List;

public class CmdDoneList extends PlotCommand {

    public CmdDoneList(PlotMe_Core instance) {
        super(instance);
    }

    public String getName() {
        return "donelist";
    }

    public boolean execute(ICommandSender sender, String[] args) {
        IPlayer player = (IPlayer) sender;
        if (manager.isPlotWorld(player)) {
            if (player.hasPermission(PermissionNames.ADMIN_DONE) || player.hasPermission(PermissionNames.USER_DONE)) {

                int page = 1;

                if (args.length == 2) {
                    page = Integer.parseInt(args[1]);
                }

                List<List<Plot>> partition = ListPartition.partition(plugin.getSqlManager().getFinishedPlots(player.getWorld()), 10);

                if (partition.isEmpty()) {
                    player.sendMessage(C("NoFinishedPlots"));
                } else {
                    player.sendMessage(C("MsgFinishedPlotsPage", page, partition.size()));

                    for (Plot plot : partition.get(page - 1)) {
                        player.sendMessage("§b" + plot.getId() + "§7 -> §f" + plot.getOwner() + "§7 @ §a" + plot.getFinishedDate() + "§r");
                    }
                }
            } else {
                return false;
            }
        } else {
            player.sendMessage(C("NotPlotWorld"));
        }
        return true;
    }

    @Override
    public String getUsage() {
        return C("CmdDoneListUsage");
    }
}
