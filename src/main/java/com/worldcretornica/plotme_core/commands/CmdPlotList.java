package com.worldcretornica.plotme_core.commands;

import com.worldcretornica.plotme_core.PermissionNames;
import com.worldcretornica.plotme_core.Plot;
import com.worldcretornica.plotme_core.PlotMe_Core;
import com.worldcretornica.plotme_core.api.ICommandSender;
import com.worldcretornica.plotme_core.api.IOfflinePlayer;
import com.worldcretornica.plotme_core.api.IPlayer;
import com.worldcretornica.plotme_core.utils.ListPartition;

import java.util.List;
import java.util.UUID;

public class CmdPlotList extends PlotCommand {

    public CmdPlotList(PlotMe_Core instance) {
        super(instance);
    }

    public String getName() {
        return "list";
    }

    public boolean execute(ICommandSender sender, String[] args) {
        IPlayer player = (IPlayer) sender;
        if (player.hasPermission(PermissionNames.USER_LIST)) {
            if (manager.isPlotWorld(player)) {
                UUID uuid;
                int page = 1;
                if (args.length >= 2) {
                    IOfflinePlayer offlinePlayer = serverBridge.getOfflinePlayer(args[1]);
                    if (offlinePlayer == null) {
                        player.sendMessage("§cNo player found by that name");
                        return true;
                    }
                    uuid = offlinePlayer.getUniqueId();
                    if (args.length == 3) {
                        page = Integer.parseInt(args[2]);
                    }
                } else {
                    uuid = player.getUniqueId();
                }

                // Get plots of that player
                List<List<Plot>> partition = ListPartition.partition(plugin.getSqlManager().getPlayerPlots(uuid), 5);
                player.sendMessage("§ePlot List" + " §7(§f" + page + "§7/§f" + partition.size() + "§7) : §r");
                for (Plot plot : partition.get(page - 1)) {
                    player.sendMessage("§ePlot ID: §b" + plot.getId().getID() + "§eWorld: §b" + plot.getWorld().getName() + "§r");

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
        return C("CmdListUsage");
    }
}
