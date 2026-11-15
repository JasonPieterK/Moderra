package com.jason.plugins.moderra.engine.config;

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
        e("filters.join-names.kick-message", "&cYour player name is not allowed on this server.",
            "&8[&6&lAntiBadWords&8]&r &c&lAccess denied. &7Your player name contains a word that is not permitted. Change your name and join again."),
        e("filters.join-names.warn-message", "&eYour player name contains a word that is not welcome here.",
            "&8[&6&lAntiBadWords&8]&r &e&lWarning. &7Your player name contains a word that is not permitted. Change your name."),
        e("filters.join-names.staff-message", "&8[&cAntiBadWords&8] &7The name of &f{player}&7 contains '&f{word}&7'.",
            "&8[&6&lAntiBadWords&8]&r &6&lStaff notice. &7The name of &f{player}&7 contains the word &c&o{word}&7."),
        e("messages.warning-broadcast-format", "&C{PLAYER} SAID THE WORD &F{WORD}&C WHICH IS NOT ALLOWED.",
            "&8[&6&lAntiBadWords&8]&r &c&lMessage blocked. &f{player}&7 used a word that is not permitted: &c&o{word}&7."),
        e("messages.warning-player", "&EWATCH YOUR WORDS. YOU ARE PUNISHED IF YOU SAY &F{REMAINING}&E BADWORDS AGAIN.",
            "&8[&6&lAntiBadWords&8]&r &e&lWarning. &7Your message contains a word that is not permitted. After &f{remaining}&7 more violations, a penalty is applied."),
        e("messages.punishment-broadcast-format", "&C{PLAYER} SAID THE WORD &F{WORD}&C WHICH IS NOT ALLOWED. FOR THEIR ACTION THEY'LL BE PUNISHED WITH &F{PUNISHMENTS}&C.",
            "&8[&6&lAntiBadWords&8]&r &c&lPenalty. &f{player}&7 used a word that is not permitted: &c&o{word}&7. The penalty is: &f{punishments}&7."),
        e("messages.punishment-player", "&CFOR YOUR BADWORDS, YOU ARE PUNISHED WITH &F{PUNISHMENTS}&C.",
            "&8[&6&lAntiBadWords&8]&r &c&lPenalty. &7You used too many words that are not permitted. The penalty is: &f{punishments}&7."),
        e("messages.punishment-disabled-player", "&EYOUR MESSAGE WAS BLOCKED. PUNISHMENT IS CURRENTLY DISABLED.",
            "&8[&6&lAntiBadWords&8]&r &c&lMessage blocked. &7Your message contains a word that is not permitted. &7&oNo penalty is applied at this time."),
        e("messages.staff-notify-format", "&8[&cAntiBadWords&8] &7Blocked '&f{word}&7' from &f{player}&7 in &f{source}&7 (&f{count}&7/&f{limit}&7).",
            "&8[&6&lAntiBadWords&8]&r &6&lStaff notice. &7Blocked &c&o{word}&7 from &f{player}&7 in &f{source}&7. Violations: &f{count}&7 of &f{limit}&7."),
        e("filters.spam.rate.message", "&cYou are sending messages too fast. Please slow down.",
            "&8[&6&lAntiBadWords&8]&r &c&lMessage blocked. &7You send messages too fast. &7&oWait a moment before you write again."),
        e("filters.spam.repeat.message", "&cPlease do not repeat the same message.",
            "&8[&6&lAntiBadWords&8]&r &c&lMessage blocked. &7You repeat the same message. &7&oWrite a different message."),
        e("filters.spam.caps.message", "&cPlease do not write in capital letters.",
            "&8[&6&lAntiBadWords&8]&r &e&lNotice. &7Your message contains too many capital letters. &7&oUse lower-case letters."),
        e("filters.spam.flood.message", "&cPlease do not flood the chat with repeated characters.",
            "&8[&6&lAntiBadWords&8]&r &e&lNotice. &7Your message contains too many repeated characters. &7&oUse normal text."),
        e("filters.spam.words.message", "&cPlease do not repeat the same word so often.",
            "&8[&6&lAntiBadWords&8]&r &c&lMessage blocked. &7Your message repeats the same word too many times. &7&oUse different words."),
        e("filters.spam.commands.message", "&cYou are using commands too fast. Please slow down.",
            "&8[&6&lAntiBadWords&8]&r &c&lCommand blocked. &7You use commands too fast. &7&oWait a moment before you use a command again."),
        e("filters.links.message", "&cLinks are not allowed here.",
            "&8[&6&lAntiBadWords&8]&r &c&lMessage blocked. &7Links are not permitted on this server."),
        e("filters.evasion.detect.message", "&cPlease write without invisible or look-alike letters.",
            "&8[&6&lAntiBadWords&8]&r &c&lMessage blocked. &7Your message contains hidden or look-alike characters. &7&oUse normal letters."),
        e("ladder.default-reason", "Bad language"),
        e("mute.messages.blocked", "&cYou are muted. Time left: {remaining}. Reason: {reason}",
            "&8[&6&lAntiBadWords&8]&r &c&lMuted. &7You cannot write now. Time left: &f{remaining}&7. Reason: &f{reason}&7."),
        e("mute.messages.join", "&cYou are still muted. Time left: {remaining}. Reason: {reason}",
            "&8[&6&lAntiBadWords&8]&r &c&lMuted. &7Your mute is still active. Time left: &f{remaining}&7. Reason: &f{reason}&7."),
        e("mute.messages.muted", "&cYou have been muted for {remaining}. Reason: {reason}",
            "&8[&6&lAntiBadWords&8]&r &c&lMuted. &7You cannot write for &f{remaining}&7. Reason: &f{reason}&7."),
        e("mute.messages.unmuted", "&aYou are no longer muted.",
            "&8[&6&lAntiBadWords&8]&r &a&lUnmuted. &7You can write again."),
        e("mute.messages.expired", "&aYour mute has run out. You can chat again.",
            "&8[&6&lAntiBadWords&8]&r &a&lMute ended. &7You can write again."),
        e("integrations.essentials.nickname-message", "&cYour nickname is not allowed on this server.",
            "&8[&6&lAntiBadWords&8]&r &c&lNickname rejected. &7Your nickname contains a word that is not permitted. &7&oChoose a different nickname."),
        e("gui.prompt.messages.chat", "&eType the new value for &f{title}&e in the chat. &7{hint}\\n&7Type &f{cancel}&7 to abort ({seconds}s).",
            "&8[&6&lAntiBadWords&8]&r &b&lInput needed. &7Type the new value for &f{title}&7 in the chat. &7&o{hint}\\n&7Type &f{cancel}&7 to cancel. Time limit: &f{seconds}&7 seconds."),
        e("gui.prompt.messages.sign", "&eWrite the new value for &f{title}&e on the sign; the lines are joined with spaces. &7{hint}\\n&7Leave it empty to abort ({seconds}s).",
            "&8[&6&lAntiBadWords&8]&r &b&lInput needed. &7Write the new value for &f{title}&7 on the sign. The lines are joined with spaces. &7&o{hint}\\n&7Leave the sign empty to cancel. Time limit: &f{seconds}&7 seconds.",
            "&8[&6&lAntiBadWords&8]&r &b&lInput needed. &7Type the new value for &f{title}&7 in the window that opens. &7&o{hint}\\n&7Sign: the lines are joined with spaces. Anvil: type in the name field, then click the result item. Leave it empty or close the window to cancel. Time limit: &f{seconds}&7 seconds."),
        e("gui.prompt.messages.error", "&c{error} &7Try again, or type &f{cancel}&7.",
            "&8[&6&lAntiBadWords&8]&r &c&lInvalid value. &7{error} &7Try again, or type &f{cancel}&7 to cancel."),
        e("gui.prompt.messages.cancelled", "&7Input cancelled.",
            "&8[&6&lAntiBadWords&8]&r &e&lCancelled. &7The input is cancelled."),
        e("gui.prompt.messages.timeout", "&7Input cancelled: no answer in time.",
            "&8[&6&lAntiBadWords&8]&r &e&lCancelled. &7The time limit ended. The input is cancelled."),
        e("discord.templates.violation", "{player} used a blocked word in {source}: {word} ({count}/{limit}, {action})"),
        e("discord.templates.punishment", "{player} was punished ({action}) at {count} strikes, step {step}"),
        e("discord.templates.mute", "{player} was muted for {duration}: {reason}"),
        e("discord.templates.unmute", "{player} was unmuted"),
        e("discord.templates.spam", "{player} triggered the spam filter ({check}: {detail}) in {source}, action {action}"),
        e("discord.templates.link", "{player} posted a link in {source}: {detail} (action {action})"),
        e("discord.templates.evasion", "{player} tried to dodge the filter in {source}: {detail} (action {action})"),
        e("discord.templates.admin-command", "{player} ran /{command}"),
        e("discord.inbound.notify-text", "Your message was not delivered to the Minecraft chat because it contains words that are not allowed on this server.",
            "[AntiBadWords] Notice. The plugin did not send your message to the Minecraft chat. It contains a word that is not permitted."),
        e("discord.webhook.username", "AntiBadWords"),
        e("discord.commands.prefix", "!abw")
    );

    /**
     * Setting key -> every earlier default list of a list setting (same rule as {@link #OLD_DEFAULTS}: a list that still equals an
     * old default is replaced; a list the admin changed is never touched). The entries keep the name the project had before 2.1.
     */
    public static final Map<String, List<List<String>>> OLD_LIST_DEFAULTS = Map.of(
        "filters.ignored-commands", List.of(List.of("antibadwords", "abw", "antiwords")),
        "integrations.outgoing-scan.ignore-containing", List.of(List.of("[AntiBadWords]"))
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
        List<String> keys = new ArrayList<>(OLD_DEFAULTS.keySet());
        keys.addAll(OLD_LIST_DEFAULTS.keySet());
        for (String key : keys) {
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
                    if (value instanceof String text && OLD_DEFAULTS.containsKey(setting.key())
                        && OLD_DEFAULTS.get(setting.key()).contains(text) && !text.equals(setting.defaultValue())) {
                        stale.add(setting);
                    } else if (value instanceof List<?> list && OLD_LIST_DEFAULTS.containsKey(setting.key())
                        && OLD_LIST_DEFAULTS.get(setting.key()).contains(texts(list)) && !list.equals(setting.defaultValue())) {
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
            info.accept("Updated " + changed.size() + " setting(s) that still had the old default text to the new default: "
                + String.join(", ", changed));
        }
        return changed;
    }

    private static List<String> texts(List<?> list) {
        List<String> out = new ArrayList<>();
        for (Object item : list) {
            out.add(String.valueOf(item));
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private static void setString(YamlEditor editor, Setting<?> setting) {
        editor.set(setting.key(), ((Setting<Object>) setting).toYaml(setting.defaultValue()));
    }
}
