package com.jason.plugins.antibadwords;

import java.util.List;

public record AntiBadWordsSettings(
    boolean punishmentEnabled,
    int violationsBeforePunish,
    List<PunishmentAction> punishmentActions,
    String violationBroadcastMessage,
    String violatorWarningMessage,
    String punishmentBroadcastMessage,
    String punishmentPlayerMessage,
    String punishmentDisabledMessage,
    boolean notifyStaff,
    String staffNotifyFormat,
    boolean finalCommandEnabled,
    String finalCommand,
    boolean collapseRepeatedLetters,
    int repeatedLettersThreshold
) {
}
