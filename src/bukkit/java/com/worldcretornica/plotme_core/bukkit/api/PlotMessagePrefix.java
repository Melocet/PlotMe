package com.worldcretornica.plotme_core.bukkit.api;

import com.worldcretornica.plotme_core.PlotMe_Core;

/**
 * Adds the canonical "[PlotMe]" tag to chat-bound messages.
 *
 * <p>This utility is the single source of truth for the player-facing chat
 * prefix. It is called from the chat-send boundary wrappers
 * ({@link BukkitPlayer#sendMessage(String)} and
 * {@link BukkitCommandSender#sendMessage(String)}). Individual call sites
 * throughout the plugin must NOT inline a "[PlotMe]" prefix of their own --
 * doing so would cause double-prefixing. To defend against that anyway,
 * {@link #apply(String)} detects messages that already start with any of the
 * known prefix variants and returns them unchanged.
 *
 * <p>Prefix behaviour follows the {@code use-legacy-texts} config flag,
 * cached on {@link PlotMe_Core}:
 * <ul>
 *   <li>Legacy mode: plain {@code "[PlotMe] "} with no color codes.</li>
 *   <li>Default mode: {@code "§6[§ePlotMe§6]§r "} -- orange brackets, yellow
 *   text, reset trailing color so the actual message keeps its own colors.</li>
 * </ul>
 *
 * <p>Multi-line messages (containing {@code \n}) are prefixed on every line
 * rather than only the first so each line in chat reads as "[PlotMe] ...".
 */
final class PlotMessagePrefix {

    /** Colored prefix used when {@code use-legacy-texts} is false (default). */
    private static final String COLORED_PREFIX = "§6[§ePlotMe§6]§r ";

    /** Plain prefix used when {@code use-legacy-texts} is true. */
    private static final String LEGACY_PREFIX = "[PlotMe] ";

    private PlotMessagePrefix() {
        // utility class
    }

    /**
     * Returns {@code message} with the canonical PlotMe prefix prepended to
     * every line. If the input is null or empty it is returned unchanged.
     * If the input already starts with one of the known prefix variants
     * (colored or plain) it is returned unchanged so we never double-prefix.
     */
    static String apply(String message) {
        if (message == null || message.isEmpty()) {
            return message;
        }
        // In legacy mode, strip any §-codes that survived caption translation
        // (literal "§c" strings in commands bypass C()'s strip step).
        if (isLegacyMode()) {
            message = stripColorCodes(message);
        }
        if (alreadyPrefixed(message)) {
            return message;
        }
        String prefix = currentPrefix();
        // Fast path: single-line message. Avoids allocating a StringBuilder
        // for the common case of a one-line caption.
        if (message.indexOf('\n') < 0) {
            return prefix + message;
        }
        // Multi-line: prefix every line. We split on '\n' rather than
        // System.lineSeparator() because Minecraft chat strings universally
        // use '\n' regardless of host platform.
        String[] lines = message.split("\n", -1);
        StringBuilder sb = new StringBuilder(message.length() + prefix.length() * lines.length);
        for (int i = 0; i < lines.length; i++) {
            if (i > 0) {
                sb.append('\n');
            }
            // Skip prefixing a blank line so we don't end up with "[PlotMe] "
            // floating on its own row.
            if (lines[i].isEmpty()) {
                continue;
            }
            if (alreadyPrefixed(lines[i])) {
                sb.append(lines[i]);
            } else {
                sb.append(prefix).append(lines[i]);
            }
        }
        return sb.toString();
    }

    /** Picks the active prefix based on the cached legacy-texts flag. */
    private static String currentPrefix() {
        return isLegacyMode() ? LEGACY_PREFIX : COLORED_PREFIX;
    }

    /** True when use-legacy-texts is on (plain text mode). */
    private static boolean isLegacyMode() {
        PlotMe_Core plugin = PlotMe_Core.getInstance();
        return plugin != null && plugin.isUseLegacyTexts();
    }

    /** Strips Bukkit § color codes (e.g. §a, §l, §r) from the input. */
    private static String stripColorCodes(String s) {
        if (s.indexOf('§') < 0) return s;
        StringBuilder sb = new StringBuilder(s.length());
        int i = 0;
        while (i < s.length()) {
            char c = s.charAt(i);
            if (c == '§' && i + 1 < s.length()) {
                char next = Character.toLowerCase(s.charAt(i + 1));
                if ((next >= '0' && next <= '9') || (next >= 'a' && next <= 'f')
                        || (next >= 'k' && next <= 'o') || next == 'r' || next == 'x') {
                    i += 2;
                    continue;
                }
            }
            sb.append(c);
            i++;
        }
        return sb.toString();
    }

    /**
     * Detects whether {@code message} already begins with one of the known
     * prefix variants (colored or plain, with or without trailing space).
     * Used to make {@link #apply(String)} idempotent.
     */
    private static boolean alreadyPrefixed(String message) {
        // Plain variant.
        if (message.startsWith("[PlotMe]")) {
            return true;
        }
        // Colored variant. We check just the "§6[§ePlotMe§6]" core because
        // some agents may have inlined the prefix without the trailing §r,
        // or with a slightly different trailing reset/color.
        if (message.startsWith("§6[§ePlotMe§6]")) {
            return true;
        }
        // Tolerate raw '&' form too -- a caption that hard-codes "&6[&ePlotMe&6]"
        // would otherwise get a second prefix tacked on before C() translates it.
        if (message.startsWith("&6[&ePlotMe&6]")) {
            return true;
        }
        return false;
    }
}
