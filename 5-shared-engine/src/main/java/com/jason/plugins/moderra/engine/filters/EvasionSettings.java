package com.jason.plugins.moderra.engine.filters;

import com.jason.plugins.moderra.engine.EffectiveSettings;
import com.jason.plugins.moderra.engine.config.Resources;
import com.jason.plugins.moderra.engine.config.Section;
import com.jason.plugins.moderra.engine.config.Setting;
import com.jason.plugins.moderra.engine.config.SettingsRegistry;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Settings of feature "invisible characters and look-alike letters": {@code filters/evasion.yml} (the steps of the
 * {@link EvasionNormalizer} and the {@link EvasionCheck}) and {@code filters/confusables.yml} (the tables).
 */
public final class EvasionSettings {
    public static final String FILE = "filters/evasion.yml";
    public static final String TABLES = "filters/confusables.yml";
    public static final String PERMISSION_BYPASS = "moderra.bypass.evasion";
    private static final String CATEGORY = "Evasion";
    private static final String TABLE_CATEGORY = "Look-alike tables";
    private static final String N = "filters.evasion.normalise.";
    private static final String D = "filters.evasion.detect.";

    public static final Setting<Boolean> STRIP_INVISIBLE = step("strip-invisible-characters", true, "Remove invisible characters",
        "Invisible characters (zero-width spaces and joiners, direction marks, soft hyphens, variation selectors, empty\n"
            + "filler letters) are removed before words are compared, so a word with a hidden character inside is still found.\n"
            + "When false they split the word in two.");
    public static final Setting<Boolean> COMPATIBILITY_FOLD = step("fold-compatibility-forms", true, "Fold special letter forms",
        "Full-width letters, circled letters, mathematical bold/italic letters and similar special forms are read as the\n"
            + "plain letter (Unicode NFKC folding).");
    public static final Setting<Boolean> FOLD_DIACRITICS = step("fold-diacritics", true, "Ignore accents",
        "Accents and other marks on letters are ignored, so \"fück\" is read as \"fuck\" and \"cabron\" matches \"cabrón\".");
    public static final Setting<List<String>> KEEP_DIACRITICS = Setting.list(N + "keep-diacritics-in-scripts", List.of())
        .file(FILE).category(CATEGORY).title("Scripts that keep their accents").overridable()
        .description("Writing systems in which marks are NOT ignored, because there they change the word. Empty = ignore them\n"
            + "everywhere (this is how the plugin always worked). Names as in Unicode, for example: latin, cyrillic, greek,\n"
            + "arabic, hebrew, devanagari, bengali, thai. Example: [devanagari, arabic] keeps Hindi vowel signs and Arabic vowel marks;\n"
            + "[latin] makes \"ano\" and \"año\" different words.").build();
    public static final Setting<Boolean> LOOK_ALIKES = step("look-alike-letters", true, "Read look-alike letters",
        "Letters from other alphabets that look like a Latin letter (Cyrillic \"с\", Greek \"ο\" ...) are read as that Latin\n"
            + "letter when they stand inside a word that also has Latin letters (\"fuсk\"). The table is in filters/confusables.yml.");
    public static final Setting<Boolean> LOOK_ALIKES_REVERSE = step("look-alike-letters-reverse", true, "Read look-alikes the other way round",
        "Also catch the opposite trick: Latin letters inside a word of another alphabet (a Russian word written with a Latin \"x\").\n"
            + "Only words that mix two alphabets are affected.");
    public static final Setting<Boolean> LOOK_ALIKES_WHOLE = step("look-alike-whole-words", false, "Read whole look-alike words",
        "Also read words that are written COMPLETELY in look-alike letters of another alphabet as Latin words.\n"
            + "Off by default: on servers with Russian or Greek players a normal word can then look like a blocked Latin word.");
    public static final Setting<Boolean> LEET = step("leetspeak", true, "Read leetspeak",
        "Symbols and digits that stand for letters are read as letters (\"sh1t\", \"@ss\", \"$hit\"). The table is in filters/confusables.yml.");
    public static final Setting<String> IGNORED_SEPARATORS = Setting.string(N + "ignored-separators", "")
        .file(FILE).category(CATEGORY).title("Separators ignored inside words").overridable()
        .description("Characters that are skipped when they stand between two letters, so \"fu.ck\" or \"fu-ck\" is read as one word.\n"
            + "Example: \".-_\". Empty by default because it also glues together things like \"e.g\" or \"well-known\".\n"
            + "Related settings in config.yml: filters.matching.collapse-repeated-letters, detect-spaced-letters, join-adjacent-words.").build();

    public static final Setting<String> DETECT_ACTION = Setting.choice(D + "action", FilterAction.LOG_ONLY, FilterAction.OFF,
            FilterAction.LOG_ONLY, FilterAction.CENSOR, FilterAction.BLOCK)
        .file(FILE).category(CATEGORY).title("When a bypass trick is seen").overridable()
        .description("What happens with a message that uses a bypass trick (see the two switches below), even when no blocked word\n"
            + "was found in it:\n"
            + "  off      = nothing\n"
            + "  log-only = the message goes through; the console and the event log note it\n"
            + "  censor   = the hidden characters are removed and look-alike letters are replaced by normal ones\n"
            + "             (chat on Bukkit/Spigot/Paper/Purpur; everywhere else the message is blocked instead)\n"
            + "  block    = the message is not delivered").build();
    public static final Setting<Boolean> DETECT_INVISIBLE = Setting.bool(D + "invisible-characters", true)
        .file(FILE).category(CATEGORY).title("Notice invisible characters").overridable()
        .description("Notice an invisible character between two letters of a word.").build();
    public static final Setting<Boolean> DETECT_MIXED = Setting.bool(D + "mixed-alphabets", true)
        .file(FILE).category(CATEGORY).title("Notice mixed alphabets").overridable()
        .description("Notice a word that mixes Latin letters with look-alike letters of another alphabet.").build();
    public static final Setting<List<String>> DETECT_SCRIPTS = Setting.list(D + "scripts", List.of("latin", "cyrillic", "greek"))
        .file(FILE).category(CATEGORY).title("Alphabets watched for invisible characters").overridable()
        .description("An invisible character only counts as a trick between two letters of these writing systems. Languages such as\n"
            + "Persian, Arabic and Hindi (and emoji) need invisible joiners for correct spelling, so they are not in the list.").build();
    /** The list of earlier versions (no Discord). Migrated installs keep it. */
    public static final List<String> LEGACY_DETECT_SOURCES = List.of("chat", "command", "sign", "book", "anvil");
    public static final Setting<List<String>> DETECT_SOURCES = Setting.list(D + "sources", List.of("chat", "command", "sign", "book", "anvil", "discord-in"))
        .file(FILE).category(CATEGORY).title("Where bypass tricks are noticed").overridable()
        .description("Text sources this applies to: chat, command, sign, book, anvil, join, command-block, discord-in (Discord messages relayed\n"
            + "by DiscordSRV; Bukkit only) or \"*\" for all. New installs include discord-in; upgraded installs keep their old list.").build();
    public static final Setting<Integer> DETECT_WEIGHT = Setting.integer(D + "strike-weight", 0).range(0, 100)
        .file(FILE).category(CATEGORY).title("Strikes for a bypass trick").overridable()
        .description("How many strikes a noticed trick adds. 0 = none (the player is never punished for it).").build();
    public static final Setting<String> DETECT_MESSAGE = Setting.string(D + "message", "&8[&6&lModerra&8]&r &c&lMessage blocked. &7Your message contains hidden or look-alike characters. &7&oUse normal letters.")
        .file(FILE).category(CATEGORY).title("Message when blocked").overridable()
        .description("Sent to the player when the message was blocked for a bypass trick. Empty = no message. Colours: & codes.").build();

    public static final Section LOOK_ALIKE_TABLE = new Section("look-alikes", TABLES, TABLE_CATEGORY, "Look-alike letters",
        "Letters that look like a plain Latin letter. Each line holds items of the form <letter>=<what it looks like>,\n"
            + "separated by spaces. The group names (cyrillic, greek ...) are only for you; add your own lines or groups.\n"
            + "Letters are read as the Latin letter only inside a word that also has Latin letters. One group is special:\n"
            + "the letters under \"always\" (small capitals) are read as the plain letter everywhere.\n"
            + "The default table was made from the Unicode confusables data (Unicode License v3, see NOTICE).\n"
            + "If this part is empty or cannot be read, the built-in table is used.\n"
            + "This part cannot be edited in the in-game menu; edit the file.",
        defaultTable(), false);
    public static final Setting<String> LEET_SYMBOLS = Setting.string("leet-symbols", "@=a $=s !=i |=i +=t")
        .file(TABLES).category(TABLE_CATEGORY).title("Leetspeak symbols")
        .description("Symbols that stand for a letter and may appear inside a word. Form: <symbol>=<letter>, separated by spaces.").build();
    public static final Setting<String> LEET_NO_START = Setting.string("leet-symbols-not-at-word-start", EvasionNormalizer.DEFAULT_NO_START)
        .file(TABLES).category(TABLE_CATEGORY).title("Symbols cut off at the start of a word")
        .description("Leetspeak symbols that are NOT read as a letter at the very start of a word (\"+1\", \"!help\", \"@Steve\").").build();
    public static final Setting<String> LEET_NO_END = Setting.string("leet-symbols-not-at-word-end", EvasionNormalizer.DEFAULT_NO_END)
        .file(TABLES).category(TABLE_CATEGORY).title("Symbols cut off at the end of a word")
        .description("Leetspeak symbols that are NOT read as a letter at the very end of a word (\"wow!\").").build();
    public static final Setting<String> LEET_DIGITS = Setting.string("leet-digits", "0=o 1=i 3=e 4=a 5=s 7=t 8=b 9=g")
        .file(TABLES).category(TABLE_CATEGORY).title("Leetspeak digits")
        .description("Digits that stand for a letter. They are only read as letters inside a word that also has Latin letters.").build();

    private EvasionSettings() {
    }

    private static Setting<Boolean> step(String name, boolean def, String title, String description) {
        return Setting.bool(N + name, def).file(FILE).category(CATEGORY).title(title).description(description).overridable().build();
    }

    private static String defaultTable() {
        String text = Resources.text("/moderra-defaults/look-alikes.yml");
        return text == null ? "cyrillic: \"а=a с=c е=e о=o р=p х=x у=y к=k м=m н=h т=t і=i\"\n" : text;
    }

    public static void register(SettingsRegistry r) {
        r.fileHeader(FILE, "Moderra - bypass tricks (invisible characters, look-alike letters, leetspeak)\n"
            + "Part 1 (normalise) says how text is cleaned up BEFORE it is compared with the word lists. These steps never\n"
            + "change what players see, and normal text gives the same result with or without them.\n"
            + "Part 2 (detect) can also note, clean or block a message just for using such a trick.\n"
            + "Players with the permission moderra.bypass.evasion are not checked by part 2. Reload with /moderra reload.");
        r.fileHeader(TABLES, "Moderra - tables for look-alike letters and leetspeak\n"
            + "Used by filters/evasion.yml. You can add lines; you rarely need to. Reload with /moderra reload.");
        r.register(STRIP_INVISIBLE);
        r.register(COMPATIBILITY_FOLD);
        r.register(FOLD_DIACRITICS);
        r.register(KEEP_DIACRITICS);
        r.register(LOOK_ALIKES);
        r.register(LOOK_ALIKES_REVERSE);
        r.register(LOOK_ALIKES_WHOLE);
        r.register(LEET);
        r.register(IGNORED_SEPARATORS);
        r.register(DETECT_ACTION);
        r.register(DETECT_INVISIBLE);
        r.register(DETECT_MIXED);
        r.register(DETECT_SCRIPTS);
        r.register(DETECT_SOURCES);
        r.register(DETECT_WEIGHT);
        r.register(DETECT_MESSAGE);
        r.register(LEET_SYMBOLS);
        r.register(LEET_NO_START);
        r.register(LEET_NO_END);
        r.register(LEET_DIGITS);
        r.register(LOOK_ALIKE_TABLE);
    }

    /** The look-alike table of these settings. */
    public static Confusables table(EffectiveSettings s, Consumer<String> warn) {
        return Confusables.parse(s.section(LOOK_ALIKE_TABLE), w -> warn.accept(TABLES + " -> " + w));
    }

    /** Builds the normaliser for these settings. Wrong table entries are skipped with a warning; nothing here can fail. */
    public static EvasionNormalizer normalizer(EffectiveSettings s, Consumer<String> warn) {
        Map<Character, Character> symbols = charMap(s.get(LEET_SYMBOLS), LEET_SYMBOLS, false, warn);
        Map<Character, Character> digits = charMap(s.get(LEET_DIGITS), LEET_DIGITS, true, warn);
        return new EvasionNormalizer(new EvasionNormalizer.Options(s.get(STRIP_INVISIBLE), true, s.get(COMPATIBILITY_FOLD),
            s.get(FOLD_DIACRITICS), scripts(s.get(KEEP_DIACRITICS), KEEP_DIACRITICS, warn), s.get(LOOK_ALIKES), s.get(LOOK_ALIKES_REVERSE),
            s.get(LOOK_ALIKES_WHOLE), s.get(LEET), symbols, s.get(LEET_NO_START), s.get(LEET_NO_END), digits,
            s.get(IGNORED_SEPARATORS), table(s, warn)));
    }

    /** {@code "@=a $=s"} -> map. Left side: one plain symbol (or digit); right side: one letter a-z. */
    private static Map<Character, Character> charMap(String text, Setting<String> setting, boolean digits, Consumer<String> warn) {
        Map<Character, Character> out = new HashMap<>();
        for (String item : text.trim().split("\\s+")) {
            if (item.isEmpty()) {
                continue;
            }
            char from = item.charAt(0);
            char to = item.length() == 3 && item.charAt(1) == '=' ? Character.toLowerCase(item.charAt(2)) : 0;
            boolean leftOk = digits ? from >= '0' && from <= '9'
                : from > 32 && from < 127 && !Character.isLetterOrDigit(from) && from != EvasionNormalizer.WILDCARD;
            if (!leftOk || to < 'a' || to > 'z') {
                warn.accept(TABLES + " -> " + setting.key() + ": '" + item + "' is not of the form "
                    + (digits ? "<digit>" : "<symbol>") + "=<letter>; it is ignored.");
                continue;
            }
            out.put(from, to);
        }
        return out;
    }

    /** Unicode script names ({@code latin}, {@code cyrillic} ...) as a set; unknown names are skipped with a warning. */
    public static Set<Character.UnicodeScript> scripts(List<String> names, Setting<?> setting, Consumer<String> warn) {
        Set<Character.UnicodeScript> out = EnumSet.noneOf(Character.UnicodeScript.class);
        for (String name : names) {
            try {
                out.add(Character.UnicodeScript.forName(name.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_')));
            } catch (IllegalArgumentException ex) {
                warn.accept(setting.file() + " -> " + setting.key() + ": '" + name + "' is not a known writing system; it is ignored.");
            }
        }
        return out;
    }
}
