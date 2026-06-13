package com.worldcretornica.plotme_core.commands;

import com.worldcretornica.plotme_core.PermissionNames;
import com.worldcretornica.plotme_core.PlotMe_Core;
import com.worldcretornica.plotme_core.api.ICommandSender;
import com.worldcretornica.plotme_core.api.IPlayer;
import com.worldcretornica.plotme_core.utils.ListPartition;

import java.util.List;

public class CmdBiomes extends PlotCommand {

    public CmdBiomes(PlotMe_Core instance) {
        super(instance);
    }

    public String getName() {
        return "biomes";
    }

    public boolean execute(ICommandSender sender, String[] args) {
        IPlayer player = (IPlayer) sender;
        if (manager.isPlotWorld(player)) {
            if (player.hasPermission(PermissionNames.USER_BIOME)) {
                int page = 1;
                List<List<String>> partition = ListPartition.partition(serverBridge.getBiomes(), 10);
                if (args.length == 2) {
                    page = Integer.parseInt(args[1]);
                }

                player.sendMessage(C("WordBiomes") + " §7(§f" + page + "§7/§f" + partition.size() + "§7) : §r");
                for (String s : partition.get(page - 1)) {
                    player.sendMessage("§f" + s);
                }
            } else {
                return false;
            }
        }
        return true;
    }

    @Override
    public String getUsage() {
        return C("CmdBiomesUsage");
    }

}
