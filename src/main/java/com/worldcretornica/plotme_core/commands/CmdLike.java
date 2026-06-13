package com.worldcretornica.plotme_core.commands;

import com.worldcretornica.plotme_core.PermissionNames;
import com.worldcretornica.plotme_core.Plot;
import com.worldcretornica.plotme_core.PlotMe_Core;
import com.worldcretornica.plotme_core.api.ICommandSender;
import com.worldcretornica.plotme_core.api.IPlayer;
import com.worldcretornica.plotme_core.api.IWorld;

import java.text.MessageFormat;

public class CmdLike extends PlotCommand {

    public CmdLike(PlotMe_Core instance) {
        super(instance);
    }

    public String getName() {
        return "like";
    }

    public boolean execute(ICommandSender sender, String[] args) {
       IPlayer player = (IPlayer) sender;
        if (player.hasPermission(PermissionNames.USER_LIKE)) {
            IWorld world = player.getWorld();
            if (manager.isPlotWorld(world)) {
                Plot plot = manager.getPlot(player);

                if (plot == null) {
                    player.sendMessage(C("NoPlotFound"));
                    return true;
                }
                if (plot.canPlayerLike(player.getUniqueId())) {
                    plot.addLike(1, player.getUniqueId());
                    plugin.getSqlManager().savePlot(plot);
                    player.sendMessage(MessageFormat.format("§aAdded like to plot §b{0}§r", plot.getId().getID()));
                } else {
                    plot.removeLike(1, player.getUniqueId());
                    player.sendMessage(MessageFormat.format("§eRemoved like from plot §b{0}§r", plot.getId().getID()));
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
        return C("CmdLikeUsage");
    }

}