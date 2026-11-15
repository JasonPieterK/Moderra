package com.jason.plugins.antibadwords.engine.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Brings message settings to the current default text. A message that still equals the default of an older version
 * (the admin did not change it) is replaced by the new default. A message the admin changed is never touched.
 *
 * <p>When a later version changes a message default again, add the text that is replaced to {@link #OLD_DEFAULTS}.
 */
public final class MessageDefaults {
    private static Map.Entry<String, List<String>> e(String key, String... olds) {
        return Map.entry(key, List.of(olds));
    }

    /** Setting key -> every earlier default text of that message. */
    public static final Map<String, List<String>> OLD_DEFAULTS = Map.ofEntries(
        e("filters.join-names.kick-message", "&cYour player name is not allowed on this server."),
        e("filters.join-names.warn-message", "&eYour player name contains a word that is not welcome here."),
        e("filters.join-names.staff-message", "&8[&cAntiBadWords&8] &7The name of &f{player}&7 contains '&f{word}&7'."),
        e("messages.warning-broadcast-format", "&C{PLAYER} SAID THE WORD &F{WORD}&C WHICH IS NOT ALLOWED."),
        e("messages.warning-player", "&EWATCH YOUR WORDS. YOU ARE PUNISHED IF YOU SAY &F{REMAINING}&E BADWORDS AGAIN."),
        e("messages.punishment-broadcast-format", "&C{PLAYER} SAID THE WORD &F{WORD}&C WHICH IS NOT ALLOWED. FOR THEIR ACTION THEY'LL BE PUNISHED WITH &F{PUNISHMENTS}&C."),
        e("messages.punishment-player", "&CFOR YOUR BADWORDS, YOU ARE PUNISHED WITH &F{PUNISHMENTS}&C."),
        e("messages.punishment-disabled-player", "&EYOUR MESSAGE WAS BLOCKED. PUNISHMENT IS CURRENTLY DISABLED."),
        e("messages.staff-notify-format", "&8[&cAntiBadWords&8] &7Blocked '&f{word}&7' from &f{player}&7 in &f{source}&7 (&f{count}&7/&f{limit}&7)."),
        e("filters.spam.rate.message", "&cYou are sending messages too fast. Please slow down."),
        e("filters.spam.repeat.message", "&cPlease do not repeat the same message."),
        e("filters.spam.caps.message", "&cPlease do not write in capital letters."),
        e("filters.spam.flood.message", "&cPlease do not flood the chat with repeated characters."),
        e("filters.spam.words.message", "&cPlease do not repeat the same word so often."),
        e("filters.spam.commands.message", "&cYou are using commands too fast. Please slow down."),
        e("filters.links.message", "&cLinks are not allowed here."),
        e("filters.evasion.detect.message", "&cPlease write without invisible or look-alike letters."),
        e("ladder.default-reason", "Bad language"),
        e("mute.messages.blocked", "&cYou are muted. Time left: {remaining}. Reason: {reason}"),
        e("mute.messages.join", "&cYou are still muted. Time left: {remaining}. Reason: {reason}"),
        e("mute.messages.muted", "&cYou have been muted for {remaining}. Reason: {reason}"),
        e("mute.messages.unmuted", "&aYou are no longer muted."),
        e("mute.messages.expired", "&aYour mute has run out. You can chat again."),
        e("integrations.essentials.nickname-message", "&cYour nickname is not allowed on this server."),
        e("gui.prompt.messages.chat", "&eType the new value for &f{title}&e in the chat. &7{hint}\\n&7Type &f{cancel}&7 to abort ({seconds}s)."),
        e("gui.prompt.messages.sign", "&eWrite the new value for &f{title}&e on the sign; the lines are joined with spaces. &7{hint}\\n&7Leave it empty to abort ({seconds}s)."),
        e("gui.prompt.messages.error", "&c{error} &7Try again, or type &f{cancel}&7."),
        e("gui.prompt.messages.cancelled", "&7Input cancelled."),
        e("gui.prompt.messages.timeout", "&7Input cancelled: no answer in time.")
    );

    private MessageDefaults() {
    }

    /**
     * Replaces old default texts in the files by the current defaults.
     *
     * @return the keys that were changed
     */
    public static List<String> upgrade(Path folder, SettingsRegistry registry, ConfigStore store, Consumer<String> info) {
        List<String> changed = new ArrayList<>();
        Map<String, List<Setting<?>>> byFile = new LinkedHashMap<>();
        for (String key : OLD_DEFAULTS.keySet()) {
            Setting<?> setting = registry.get(key);
            if (setting != null) {
                byFile.computeIfAbsent(setting.file(), f -> new ArrayList<>()).add(setting);
            }
        }
        for (Map.Entry<String, List<Setting<?>>> file : byFile.entrySet()) {
            Path path = folder.resolve(file.getKey());
            if (Files.notExists(path)) {
                continue;
            }
            List<Setting<?>> stale = new ArrayList<>();
            try {
                YamlTree tree = YamlTree.parse(Files.readString(path, StandardCharsets.UTF_8));
                for (Setting<?> setting : file.getValue()) {
                    Object value = tree.get(setting.key());
                    if (value instanceof String text && OLD_DEFAULTS.get(setting.key()).contains(text)
                        && !text.equals(setting.defaultValue())) {
                        stale.add(setting);
                    }
                }
                if (stale.isEmpty()) {
                    continue;
                }
                store.edit(file.getKey(), editor -> stale.forEach(s -> setString(editor, s)));
            } catch (IOException | RuntimeException ex) {
                continue; // an unreadable file is reported by the normal load
            }
            stale.forEach(s -> changed.add(s.key()));
        }
        if (!changed.isEmpty()) {
            info.accept("Updated " + changed.size() + " message(s) that still had the old default text to the new default: "
                + String.join(", ", changed));
        }
        return changed;
    }

    @SuppressWarnings("unchecked")
    private static void setString(YamlEditor editor, Setting<?> setting) {
        editor.set(setting.key(), ((Setting<Object>) setting).toYaml(setting.defaultValue()));
    }
}
