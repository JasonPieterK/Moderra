package com.jason.plugins.moderra;

import java.util.List;

/**
 * One configured punishment (a single entry under {@code punishment.effects}, {@code .attributes},
 * {@code .sounds} or {@code .commands}).
 *
 * @param enabled whether the action runs when a player is punished
 * @param type    {@code effect}, {@code attribute}, {@code sound} or {@code command}
 * @param id      the config key (for log messages)
 * @param args    parsed arguments; the meaning depends on {@code type}:
 *                <ul>
 *                  <li>effect: name, amplifier, duration (seconds)</li>
 *                  <li>attribute: name, value, duration (seconds)</li>
 *                  <li>sound: name, volume, pitch</li>
 *                  <li>command: the command line</li>
 *                </ul>
 */
public record PunishmentAction(
    boolean enabled,
    String type,
    String id,
    List<String> args
) {
}
