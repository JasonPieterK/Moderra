package com.jason.plugins.antibadwords;

import java.util.List;

public record AntiBadWordsSettings(
    int violationsBeforePunish,
    List<PunishmentAction> punishmentActions,
    String warningMessage,
    String warningBroadcastFormat,
    boolean broadcastWarningToServer,
    String punishmentMessage,
    boolean notifyStaff,
    String staffNotifyFormat,
    boolean collapseRepeatedLetters,
    int repeatedLettersThreshold
) {
}
