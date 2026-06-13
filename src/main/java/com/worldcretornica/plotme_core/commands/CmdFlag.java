package com.worldcretornica.plotme_core.commands;

import com.worldcretornica.plotme_core.PermissionNames;
import com.worldcretornica.plotme_core.Plot;
import com.worldcretornica.plotme_core.PlotMe_Core;
import com.worldcretornica.plotme_core.api.ICommandSender;
import com.worldcretornica.plotme_core.api.IPlayer;
import com.worldcretornica.plotme_core.api.IWorld;
import com.worldcretornica.plotme_core.flag.PlotFlag;
import com.worldcretornica.plotme_core.flag.PlotFlagRegistry;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * {@code /plotme flag} -- list / inspect / set / reset per-plot flags.
 *
 * Usage forms:
 * <ul>
 *   <li>{@code /plotme flag}            -- list every registered flag with
 *                                          its current effective value on
 *                                          the player's current plot.</li>
 *   <li>{@code /plotme flag <name>}     -- show the current effective value
 *                                          for one flag (and whether it's
 *                                          the default or an override).</li>
 *   <li>{@code /plotme flag <name> <v>} -- parse and store {@code v}.</li>
 *   <li>{@code /plotme flag <name> reset|unset|null}
 *                                       -- remove the override.</li>
 * </ul>
 *
 * Plot owners may freely modify their own plot's flags ({@code plotme.use.flag}).
 * Admins with {@code plotme.admin.flag} may modify flags on any plot they
 * happen to be standing on.
 */
public class CmdFlag extends PlotCommand {

    private static final List<String> RESET_TOKENS = java.util.Arrays.asList("reset", "unset", "null", "default", "clear");

    public CmdFlag(PlotMe_Core instance) {
        super(instance);
    }

    @Override
    public List<String> getAliases() {
        return Collections.singletonList("flags");
    }

    @Override
    public String getName() {
        return "flag";
    }

    @Override
    public boolean execute(ICommandSender sender, String[] args) {
        if (!(sender instanceof IPlayer)) {
            sender.sendMessage("§cFlags can only be managed in-game.");
            return true;
        }
        IPlayer player = (IPlayer) sender;

        boolean isAdmin = player.hasPermission(PermissionNames.ADMIN_FLAG);
        if (!isAdmin && !player.hasPermission(PermissionNames.USER_FLAG)) {
            return false;
        }

        IWorld world = player.getWorld();
        if (!manager.isPlotWorld(world)) {
            player.sendMessage(C("NotPlotWorld"));
            return true;
        }

        Plot plot = manager.getPlot(player);
        if (plot == null) {
            player.sendMessage(C("NoPlotFound"));
            return true;
        }

        // Ownership / membership check: owners always pass; admins pass via
        // the admin permission. Trusted/allowed members may *view* but not
        // *set*, to mirror existing PlotMe semantics where building rights
        // don't imply config rights.
        boolean isOwner = plot.getOwnerId().equals(player.getUniqueId());
        boolean canModify = isOwner || isAdmin;

        if (args.length == 0) {
            listFlags(player, plot);
            return true;
        }

        String flagName = args[0];
        PlotFlag<?> flag = PlotFlagRegistry.get(flagName);
        if (flag == null) {
            player.sendMessage("§cUnknown flag: §f" + flagName);
            player.sendMessage("§7Try '§f/plotme flag§7' to see the list.");
            return true;
        }

        if (args.length == 1) {
            showFlag(player, plot, flag);
            return true;
        }

        if (!canModify) {
            player.sendMessage(C("MsgDoNotOwnPlot"));
            return true;
        }

        // Join everything after the flag name -- list-typed flags want
        // commas embedded in the single value, but we accept space-separated
        // tokens for convenience and reassemble.
        StringBuilder rawBuilder = new StringBuilder();
        for (int i = 1; i < args.length; i++) {
            if (i > 1) {
                rawBuilder.append(' ');
            }
            rawBuilder.append(args[i]);
        }
        String raw = rawBuilder.toString().trim();

        if (RESET_TOKENS.contains(raw.toLowerCase(Locale.ROOT))) {
            plot.resetFlagValue(flag);
            plugin.getSqlManager().savePlot(plot);
            player.sendMessage("§aFlag '§b" + flag.getName() + "§a' reset to default (§f"
                    + serializeDefault(flag) + "§a).");
            return true;
        }

        try {
            applyAndSave(plot, flag, raw);
            player.sendMessage("§aFlag '§b" + flag.getName() + "§a' set to §f" + raw + "§a.");
        } catch (IllegalArgumentException ex) {
            player.sendMessage("§cBad value: §f" + ex.getMessage());
            player.sendMessage("§7Expected §f" + flag.getValueHint() + "§7.");
        }
        return true;
    }

    private void listFlags(IPlayer player, Plot plot) {
        player.sendMessage("§eFlags on this plot (§c*§e = overridden):");
        Map<String, String> overrides = plot.getAllFlagValues();
        for (PlotFlag<?> flag : PlotFlagRegistry.all()) {
            String value = renderCurrentValue(plot, flag);
            String marker = overrides.containsKey(flag.getName()) ? "§c* " : "§7  ";
            player.sendMessage(marker + "§b" + flag.getName() + " §7= §f" + value);
        }
    }

    private void showFlag(IPlayer player, Plot plot, PlotFlag<?> flag) {
        boolean overridden = plot.hasFlagValue(flag);
        String value = renderCurrentValue(plot, flag);
        player.sendMessage("§b" + flag.getName() + " §7= §f" + value + (overridden ? " §e(override)" : " §7(default)"));
        player.sendMessage("§7  expected value: §f" + flag.getValueHint());
    }

    private <T> String renderCurrentValue(Plot plot, PlotFlag<T> flag) {
        T value = plot.getFlagValue(flag);
        return flag.serialize(value);
    }

    private <T> String serializeDefault(PlotFlag<T> flag) {
        return flag.serialize(flag.getDefaultValue());
    }

    // Generic dance so we can parse-then-store with the right T without
    // exposing wildcard mess to the caller.
    private <T> void applyAndSave(Plot plot, PlotFlag<T> flag, String raw) {
        T parsed = flag.parse(raw);
        plot.setFlagValue(flag, parsed);
        plugin.getSqlManager().savePlot(plot);
    }

    @Override
    public String getUsage() {
        return "/plotme flag [<name> [<value>|reset]]";
    }
}
