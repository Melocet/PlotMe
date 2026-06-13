package com.worldcretornica.plotme_core.commands;

import com.worldcretornica.plotme_core.AccessLevel;
import com.worldcretornica.plotme_core.PermissionNames;
import com.worldcretornica.plotme_core.Plot;
import com.worldcretornica.plotme_core.PlotMe_Core;
import com.worldcretornica.plotme_core.api.ICommandSender;
import com.worldcretornica.plotme_core.api.IPlayer;
import com.worldcretornica.plotme_core.api.IWorld;

/**
 * {@code /plotme access <player> <level>} — assigns a fine-grained
 * {@link AccessLevel} to a player on the current plot.
 *
 * <p>Complement to {@link CmdAdd} and {@link CmdTrust}, which remain as shorthands
 * for {@code BUILD} level. The level here is persisted in plot metadata under the
 * {@code plotme_core_access} namespace (see {@link Plot#getAccessLevel(java.util.UUID)}),
 * so this does not change the {@code plotmecore_allowed} table schema. The legacy
 * {@code allowed} membership is also set so existing listeners continue to see the
 * player as a member.</p>
 */
public class CmdAccess extends PlotCommand {

    public CmdAccess(PlotMe_Core instance) {
        super(instance);
    }

    @Override
    public String getName() {
        return "access";
    }

    @Override
    public boolean execute(ICommandSender sender, String[] args) {
        if (args.length != 3) {
            sender.sendMessage(getUsage());
            return true;
        }
        if (!(sender instanceof IPlayer)) {
            sender.sendMessage("§cThis command must be run by a player.");
            return true;
        }
        IPlayer player = (IPlayer) sender;
        if (!(player.hasPermission(PermissionNames.ADMIN_ADD) || player.hasPermission(PermissionNames.USER_ADD))) {
            return false;
        }
        if ("*".equals(args[1]) && plugin.getConfig().getBoolean("disableWildCard")) {
            sender.sendMessage(C("WildcardsDisabled"));
            return true;
        }

        AccessLevel level = AccessLevel.fromString(args[2]);
        if (level == null) {
            player.sendMessage("§cUnknown access level '§f" + args[2] + "§c'. Valid: §fCONTAINER, INTERACT, BUILD, MANAGE§c.");
            return true;
        }

        IWorld world = player.getWorld();
        if (!manager.isPlotWorld(player)) {
            player.sendMessage(C("NotPlotWorld"));
            return true;
        }
        Plot plot = manager.getPlot(player);
        if (plot == null) {
            player.sendMessage(C("NoPlotFound"));
            return true;
        }
        if (!(player.getUniqueId().equals(plot.getOwnerId()) || player.hasPermission(PermissionNames.ADMIN_ADD))) {
            player.sendMessage(C("MsgThisPlot") + "§7(§b" + plot.getId() + "§7) §r" + C("MsgNotYoursNotAllowedAdd"));
            return true;
        }

        String targetKey;
        if ("*".equals(args[1])) {
            targetKey = "*";
        } else if (serverBridge.getPlayer(args[1]) != null) {
            targetKey = serverBridge.getPlayer(args[1]).getUniqueId().toString();
        } else {
            player.sendMessage("§b" + args[1] + "§c was not found. Are they online?");
            return true;
        }

        // Persist the fine-grained level via plot metadata, then mirror to legacy membership
        // so listeners that still use Plot.isMember(...) continue to see the player.
        plot.setPlotProperty("plotme_core_access", targetKey, level.name());
        // Legacy mapping: BUILD/MANAGE behave like TRUSTED (the higher legacy tier the
        // existing listener treats as "trusted even when owner offline"); CONTAINER/INTERACT
        // behave like ALLOWED.
        Plot.AccessLevel legacy = level.includes(AccessLevel.BUILD)
                ? Plot.AccessLevel.TRUSTED
                : Plot.AccessLevel.ALLOWED;
        plot.addMember(targetKey, legacy);
        plot.removeDenied(targetKey);
        plugin.getSqlManager().savePlot(plot);

        player.sendMessage("§aSet access level for §b" + args[1] + "§a to §b" + level.name() + "§a.");
        if (isAdvancedLogging()) {
            serverBridge.getLogger().info(
                    player.getName() + " set access level " + level.name() + " for " + args[1]
                            + " on plot " + plot.getId());
        }
        return true;
    }

    @Override
    public String getUsage() {
        return "Usage: /plotme access <player> <CONTAINER|INTERACT|BUILD|MANAGE>";
    }
}
