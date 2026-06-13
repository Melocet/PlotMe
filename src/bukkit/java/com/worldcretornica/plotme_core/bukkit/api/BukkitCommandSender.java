package com.worldcretornica.plotme_core.bukkit.api;

import com.worldcretornica.plotme_core.api.ICommandSender;
import org.bukkit.command.CommandSender;

public class BukkitCommandSender implements ICommandSender {

    private final CommandSender commandsender;

    public BukkitCommandSender(CommandSender sender) {
        commandsender = sender;
    }

    /**
     * Sends a message to the wrapped command sender (console or player).
     *
     * <p>Like {@link BukkitPlayer#sendMessage(String)}, this is a canonical
     * chat boundary for PlotMe-generated output -- it automatically prepends
     * the "[PlotMe]" prefix on every line via {@link PlotMessagePrefix#apply}.
     * Call sites MUST NOT add their own "[PlotMe]" prefix; if they do, the
     * prefix helper is idempotent and will skip them, but the convention is
     * still "prefix only at the send boundary".
     */
    @Override
    public void sendMessage(String message) {
        commandsender.sendMessage(PlotMessagePrefix.apply(message));
    }

    public CommandSender getCommandSender() {
        return commandsender;
    }

    public String getName() {
        return commandsender.getName();
    }

    @Override
    public boolean hasPermission(String permission) {
        return true;
    }
}
