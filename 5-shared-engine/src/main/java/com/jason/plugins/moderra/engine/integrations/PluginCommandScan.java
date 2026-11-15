package com.jason.plugins.moderra.engine.integrations;

import com.jason.plugins.moderra.engine.Engine;
import com.jason.plugins.moderra.mod.Names;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Commands of TAB and NametagEdit that are scanned while those plugins are installed, and the {prefix}/{group} message values. */
public final class PluginCommandScan {
    private PluginCommandScan() {
    }

    /** For a command line the normal settings do not scan: the text after the label, or {@code null}. */
    public static String extraCommandText(Engine engine, Set<String> ignoredCommands, String commandLine) {
        int space = commandLine.indexOf(' ');
        if (space < 0) {
            return null;
        }
        String label = Names.commandLabel(commandLine.substring(0, space));
        if (ignoredCommands.contains(label)) {
            return null;
        }
        boolean hit = (engine.value(NameplateSettings.TAB) && listed(engine.value(NameplateSettings.TAB_COMMANDS), label)
            && engine.platform().hasPlugin("TAB"))
            || (engine.value(NameplateSettings.NAMETAGEDIT) && listed(engine.value(NameplateSettings.NAMETAGEDIT_COMMANDS), label)
            && engine.platform().hasPlugin("NametagEdit"));
        return hit ? commandLine.substring(space + 1) : null;
    }

    private static boolean listed(List<String> labels, String label) {
        for (String raw : labels) {
            if (raw.strip().toLowerCase(Locale.ROOT).equals(label)) {
                return true;
            }
        }
        return false;
    }

    /** Adds {@code prefix} and {@code group} (empty when unknown) for lines that people read. */
    public static void addGroupValues(Engine engine, UUID player, Map<String, String> values) {
        if (!engine.value(NameplateSettings.MESSAGE_PLACEHOLDERS)) {
            return;
        }
        String prefix = "";
        String group = "";
        PermissionSource source = engine.permissionSource();
        if (source != null && player != null) {
            try {
                prefix = source.prefix(player).orElse("");
                group = source.primaryGroup(player).orElse("");
            } catch (RuntimeException ex) {
                engine.platform().logger().warn("The permission plugin could not tell the prefix or group: " + ex);
            }
        }
        values.putIfAbsent("prefix", prefix);
        values.putIfAbsent("group", group);
    }
}
