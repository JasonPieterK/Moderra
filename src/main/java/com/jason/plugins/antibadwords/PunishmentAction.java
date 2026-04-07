package com.jason.plugins.antibadwords;

public record PunishmentAction(
    boolean enabled,
    String type,
    String format
) {
}
