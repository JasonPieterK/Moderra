package com.jason.plugins.moderra.engine;

import java.util.UUID;
import java.util.function.Predicate;

/**
 * One piece of text a player produced, with everything the checks need to judge it. Immutable.
 * Build it with {@link Engine#message} (fills in world and permissions from the {@link Platform}) or with {@link #of}.
 */
public final class Message {
    public static final String CHAT = "chat";
    public static final String COMMAND = "command";
    public static final String SIGN = "sign";
    public static final String BOOK = "book";
    public static final String ANVIL = "anvil";
    public static final String JOIN = "join";
    /** The back side of a sign (where the platform can tell the sides apart). */
    public static final String SIGN_BACK = "sign-back";
    /** A command stored in or run by a command block (text = the whole command line; there may be no player). */
    public static final String COMMAND_BLOCK = "command-block";

    private final UUID playerId;
    private final String playerName;
    private final String world;
    private final String dimension;
    private final String source;
    private final String text;
    private final String rawText;
    private final Predicate<String> permissions;
    private final boolean probe;
    private final String channel;

    private Message(UUID playerId, String playerName, String world, String dimension, String source, String text, String rawText,
                    Predicate<String> permissions) {
        this(playerId, playerName, world, dimension, source, text, rawText, permissions, false);
    }

    private Message(UUID playerId, String playerName, String world, String dimension, String source, String text, String rawText,
                    Predicate<String> permissions, boolean probe) {
        this(playerId, playerName, world, dimension, source, text, rawText, permissions, probe, "");
    }

    private Message(UUID playerId, String playerName, String world, String dimension, String source, String text, String rawText,
                    Predicate<String> permissions, boolean probe, String channel) {
        this.channel = channel == null ? "" : channel;
        this.probe = probe;
        this.playerId = playerId;
        this.playerName = playerName == null ? "" : playerName;
        this.world = world == null ? "" : world;
        this.dimension = dimension == null ? "" : dimension;
        this.source = source;
        this.text = text;
        this.rawText = rawText;
        this.permissions = permissions == null ? node -> false : permissions;
    }

    /**
     * @param source      where the text came from: {@link #CHAT}, {@link #COMMAND} (text = the whole command line),
     *                    {@link #SIGN}, {@link #BOOK}, {@link #ANVIL}, {@link #JOIN} or a new name
     * @param permissions answers "does this player have this permission node?"
     */
    public static Message of(UUID playerId, String playerName, String world, String source, String text, Predicate<String> permissions) {
        return new Message(playerId, playerName, world, "", source, text, text, permissions);
    }

    public Message withDimension(String dimensionName) {
        return new Message(playerId, playerName, world, dimensionName, source, text, rawText, permissions, probe, channel);
    }

    /** Same message with other text to judge; {@link #rawText()} keeps what the player really sent. */
    public Message withText(String newText) {
        return new Message(playerId, playerName, world, dimension, source, newText, rawText, permissions, probe, channel);
    }

    /**
     * Same message marked as a test ({@code /moderra check}): checks must not remember it (no spam counters) and checks
     * that need a history of earlier messages skip it.
     */
    public Message asProbe() {
        return new Message(playerId, playerName, world, dimension, source, text, rawText, permissions, true, channel);
    }

    /** Same message in a chat channel (a chat-channel plugin's channel name; empty = none / unknown). */
    public Message withChannel(String channelName) {
        return new Message(playerId, playerName, world, dimension, source, text, rawText, permissions, probe, channelName);
    }

    /** The chat channel the text was sent in, or an empty string. Additive: most sources have none. */
    public String channel() {
        return channel;
    }

    public boolean probe() {
        return probe;
    }

    /** The source a variant belongs to: {@code sign-back} -> {@code sign}; every other source is its own base. */
    public static String baseSource(String source) {
        return SIGN_BACK.equals(source) ? SIGN : source;
    }

    public UUID playerId() {
        return playerId;
    }

    public String playerName() {
        return playerName;
    }

    public String world() {
        return world;
    }

    public String dimension() {
        return dimension;
    }

    public String source() {
        return source;
    }

    /** The text the checks look at (for commands: only the arguments; later also the text after a censoring check). */
    public String text() {
        return text;
    }

    /** The text exactly as the player sent it. */
    public String rawText() {
        return rawText;
    }

    public boolean has(String permissionNode) {
        return permissions.test(permissionNode);
    }

    public Predicate<String> permissions() {
        return permissions;
    }
}
