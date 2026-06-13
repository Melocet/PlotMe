package com.worldcretornica.plotme_core.commands;

import com.worldcretornica.plotme_core.PermissionNames;
import com.worldcretornica.plotme_core.Plot;
import com.worldcretornica.plotme_core.PlotId;
import com.worldcretornica.plotme_core.PlotMapInfo;
import com.worldcretornica.plotme_core.PlotMeCoreManager;
import com.worldcretornica.plotme_core.PlotMe_Core;
import com.worldcretornica.plotme_core.api.ICommandSender;
import com.worldcretornica.plotme_core.api.IPlayer;
import com.worldcretornica.plotme_core.api.IWorld;
import com.worldcretornica.plotme_core.api.event.PlotMergeEvent;
import net.milkbowl.vault.economy.EconomyResponse;

/**
 * {@code /plotme merge <north|south|east|west>} — merges the plot the player
 * is currently standing on with the adjacent plot in the requested cardinal
 * direction.
 *
 * Preconditions:
 *   - player must be on a claimed plot they own;
 *   - the neighbour in that direction must exist and be owned by the same
 *     player;
 *   - if {@code mergeCost} is configured and economy is enabled for the
 *     world, the cost is withdrawn before the road is filled.
 *
 * On success: the road blocks between the two plots are replaced with floor
 * blocks (via the generator's {@code fillRoad}) and both plot rows record
 * each other in {@link Plot#getMergedWith()}. Cross-plot building / clearing
 * behaviour is intentionally not implemented yet — see PlotMergeEvent javadoc.
 */
public class CmdMerge extends PlotCommand {

    public CmdMerge(PlotMe_Core instance) {
        super(instance);
    }

    @Override
    public String getName() {
        return "merge";
    }

    @Override
    public boolean execute(ICommandSender sender, String[] args) {
        if (!(sender instanceof IPlayer)) {
            sender.sendMessage("§cOnly players can use /plotme merge.");
            return true;
        }
        IPlayer player = (IPlayer) sender;
        if (!player.hasPermission(PermissionNames.USER_MERGE)) {
            return false;
        }
        // Master switch — admin can disable the merge feature server-wide.
        if (!plugin.getConfig().getBoolean("merge-enabled", true)) {
            player.sendMessage(C("MsgMergeDisabled"));
            return true;
        }
        IWorld world = player.getWorld();
        if (!manager.isPlotWorld(world)) {
            player.sendMessage(C("NotPlotWorld"));
            return true;
        }
        if (args.length < 2 || args[1] == null || args[1].isEmpty()) {
            player.sendMessage(getUsage());
            return true;
        }
        String direction = args[1].toLowerCase(java.util.Locale.ROOT);
        if (!direction.equals("north") && !direction.equals("south")
                && !direction.equals("east")  && !direction.equals("west")) {
            player.sendMessage(getUsage());
            return true;
        }

        PlotId currentId = manager.getPlotId(player);
        if (currentId == null) {
            player.sendMessage(C("MsgCannotClaimRoad"));
            return true;
        }
        Plot currentPlot = manager.getPlotById(currentId, world);
        if (currentPlot == null) {
            player.sendMessage(C("MsgPlotNotFound"));
            return true;
        }
        // Only the owner (or someone with admin claim-other) may merge their plot.
        if (!currentPlot.getOwnerId().equals(player.getUniqueId())
                && !player.hasPermission(PermissionNames.ADMIN_CLAIM_OTHER)) {
            player.sendMessage(C("MsgThisPlotOwned"));
            return true;
        }

        PlotId neighbourId = PlotMeCoreManager.neighbourId(currentId, direction);
        if (neighbourId == null) {
            player.sendMessage(getUsage());
            return true;
        }
        Plot neighbour = manager.getPlotById(neighbourId, world);
        if (neighbour == null) {
            player.sendMessage(C("MsgPlotNotFound"));
            return true;
        }
        if (!neighbour.getOwnerId().equals(currentPlot.getOwnerId())) {
            player.sendMessage(C("MsgMergeNotSameOwner"));
            return true;
        }
        if (currentPlot.isMergedWith(neighbourId)) {
            player.sendMessage(C("MsgMergeAlreadyMerged"));
            return true;
        }

        // Enforce merged-cluster size cap. Admins (plotme.admin) and holders of
        // plotme.merge.limit.* bypass the check entirely. Otherwise the
        // effective max is max(perm-tier, config `merge-max`). The prospective
        // size is the union of the two clusters that would be joined; since
        // the already-merged case is rejected above, the two clusters are
        // disjoint and we can just sum them.
        boolean bypass = player.hasPermission("plotme.admin")
                || player.hasPermission("plotme.merge.limit.*");
        if (!bypass) {
            int configMax = plugin.getConfig().getInt("merge-max", 4);
            int permMax = -1;
            if (player.hasPermission("plotme.merge.limit.16")) {
                permMax = 16;
            } else if (player.hasPermission("plotme.merge.limit.9")) {
                permMax = 9;
            } else if (player.hasPermission("plotme.merge.limit.6")) {
                permMax = 6;
            } else if (player.hasPermission("plotme.merge.limit.4")) {
                permMax = 4;
            }
            int effectiveMax = (permMax > 0) ? Math.max(permMax, configMax) : configMax;

            int currentClusterSize = manager.getMergedCluster(currentPlot).size();
            int neighbourClusterSize = manager.getMergedCluster(neighbour).size();
            int prospectiveSize = currentClusterSize + neighbourClusterSize;
            if (prospectiveSize > effectiveMax) {
                player.sendMessage(String.format(C("MsgMergeLimitReached"), effectiveMax));
                return true;
            }
        }

        // Charge mergeCost from the global config (not the per-world economy
        // section, by request). Skipped when economy isn't usable or the
        // configured cost is non-positive.
        double cost = plugin.getConfig().getDouble("mergeCost", 100.0);
        PlotMapInfo pmi = manager.getMap(world);
        if (cost > 0 && manager.isEconomyEnabled(pmi)) {
            if (!serverBridge.has(player, cost)) {
                player.sendMessage(C("MsgNotEnoughBuy") + " "
                        + C("WordMissing") + " §b"
                        + serverBridge.getEconomy().get().format(cost) + "§r");
                return true;
            }
            EconomyResponse er = serverBridge.withdrawPlayer(player, cost);
            if (!er.transactionSuccess()) {
                player.sendMessage("§c" + er.errorMessage);
                serverBridge.getLogger().warning(er.errorMessage);
                return true;
            }
        }

        PlotMergeEvent event = new PlotMergeEvent(currentPlot, neighbour, player);
        plugin.getEventBus().post(event);
        if (event.isCancelled()) {
            // Refund any cost the merge wasn't allowed to consume.
            if (cost > 0 && manager.isEconomyEnabled(pmi)) {
                serverBridge.depositPlayer(player, cost);
            }
            return true;
        }

        if (manager.mergePlots(currentPlot, neighbour)) {
            player.sendMessage(C("MsgMergeSuccess"));
            // Audible confirmation, mirroring the success cue CmdClaim plays.
            player.playSound("BLOCK_NOTE_BLOCK_PLING", 1.0f, 1.6f);
            if (isAdvancedLogging()) {
                serverBridge.getLogger().info(player.getName()
                        + " merged plots " + currentId + " and " + neighbourId
                        + (cost > 0 ? " for " + cost : ""));
            }
        } else {
            player.sendMessage(C("MsgMergeFailed"));
            if (cost > 0 && manager.isEconomyEnabled(pmi)) {
                serverBridge.depositPlayer(player, cost);
            }
        }
        return true;
    }

    @Override
    public String getUsage() {
        return C("CmdMergeUsage");
    }
}
