package com.jason.plugins.antibadwords.engine.command;

/**
 * Colour codes and message helpers for command answers, written with {@code §} like Bukkit's ChatColor.
 *
 * <p>Palette: gold = brand and headings, aqua = information and hints, green = success, yellow = warning or a
 * question, red = error or block (only the key word), gray = normal text, white = values, bold = key words,
 * italic = hints. Every helper starts with {@link #PREFIX}.
 */
public final class Chat {
    public static final String YELLOW = "§e";
    public static final String RED = "§c";
    public static final String GREEN = "§a";
    public static final String GOLD = "§6";
    public static final String GRAY = "§7";
    public static final String WHITE = "§f";
    public static final String AQUA = "§b";
    public static final String DARK_GRAY = "§8";
    public static final String BOLD = "§l";
    public static final String ITALIC = "§o";
    public static final String RESET = "§r";

    /** The brand prefix in front of every message. */
    public static final String PREFIX = DARK_GRAY + "[" + GOLD + BOLD + "AntiBadWords" + DARK_GRAY + "]" + RESET + " ";

    private Chat() {
    }

    /** Success: green bold "Done." and the text. */
    public static String ok(String text) {
        return PREFIX + GREEN + BOLD + "Done. " + GRAY + text;
    }

    /** Error: red bold "Error." and the text. */
    public static String error(String text) {
        return PREFIX + RED + BOLD + "Error. " + GRAY + text;
    }

    /** Warning or question: yellow bold "Notice." and the text. */
    public static String warn(String text) {
        return PREFIX + YELLOW + BOLD + "Notice. " + GRAY + text;
    }

    /** Information: aqua bold "Info." and the text. */
    public static String info(String text) {
        return PREFIX + AQUA + BOLD + "Info. " + GRAY + text;
    }

    /** A usage line: yellow bold "Usage:" and the command in white. */
    public static String usage(String command) {
        return PREFIX + YELLOW + BOLD + "Usage: " + WHITE + command;
    }

    /** A heading line: gold bold. */
    public static String heading(String text) {
        return PREFIX + GOLD + BOLD + text;
    }

    /** A hint: gray italic. */
    public static String hint(String text) {
        return GRAY + ITALIC + text;
    }

    /** A value in white; the text after it is gray again. */
    public static String val(Object value) {
        return WHITE + value + GRAY;
    }
}
