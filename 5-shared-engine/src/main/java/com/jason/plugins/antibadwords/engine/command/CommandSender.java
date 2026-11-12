package com.jason.plugins.antibadwords.engine.command;

import java.util.UUID;

/** Whoever typed an {@code /abw} command: a player, the console or a command block. Implemented by each loader. */
public interface CommandSender {
    /** Player name, or "CONSOLE". */
    String name();

    /** The player's id; {@code null} for the console and command blocks. */
    UUID id();

    /** Permission check. The console has every permission. */
    boolean has(String permission);

    /** Sends one line (coloured with {@code §} codes) back to the sender. */
    void send(String message);
}
