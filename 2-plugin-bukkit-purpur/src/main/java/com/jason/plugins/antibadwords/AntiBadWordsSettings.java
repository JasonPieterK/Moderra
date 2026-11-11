package com.jason.plugins.antibadwords;

import java.util.List;
import java.util.Set;

/**
 * Immutable snapshot of everything read from config.yml (except the word lists, see {@link WordMatcher}).
 * A new snapshot is created on every reload, so readers never see a half-updated configuration.
 */
public record AntiBadWordsSettings(
    // --- punishment ---------------------------------------------------------------------------
    boolean punishmentEnabled,
    int violationsBeforePunish,
    int violationExpireMinutes,
    boolean clearViolationsOnQuit,
    List<PunishmentAction> punishmentActions,
    boolean finalCommandEnabled,
    String finalCommand,
    // --- messages -----------------------------------------------------------------------------
    boolean broadcastWarnings,
    String warningBroadcastMessage,
    String warningPlayerMessage,
    String punishmentBroadcastMessage,
    String punishmentPlayerMessage,
    String punishmentDisabledMessage,
    boolean notifyStaff,
    String staffNotifyFormat,
    boolean logToConsole,
    // --- where we look for bad words ----------------------------------------------------------
    boolean scanChat,
    boolean scanCommands,
    boolean scanSigns,
    boolean scanBooks,
    boolean scanAnvil,
    Set<String> scannedCommands,
    Set<String> ignoredCommands
) {
}
