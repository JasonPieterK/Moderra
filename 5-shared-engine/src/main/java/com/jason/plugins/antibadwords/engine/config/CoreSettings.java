package com.jason.plugins.antibadwords.engine.config;

import java.util.List;

/**
 * Every setting that existed in the old single {@code config.yml}, plus the few new general ones. The key paths are the
 * old ones on purpose, so an old file migrates by copying values. New features add their own class with a
 * {@code register(SettingsRegistry)} method and call it from {@code Engine.registerBuiltIns}.
 */
public final class CoreSettings {
    public static final String CONFIG = "config.yml";
    public static final String LADDER = "moderation/ladder.yml";

    private static final String LANGUAGES = "Languages";
    private static final String SCANNING = "Scanning";
    private static final String MATCHING = "Matching";
    private static final String WHITELIST_CATEGORY = "Whitelist";
    private static final String MESSAGES = "Messages";
    private static final String GENERAL = "General";
    private static final String PUNISHMENT = "Punishment";

    public static final Setting<Integer> CONFIG_VERSION = Setting.integer("config-version", 2).file(CONFIG).category(GENERAL)
        .title("Config version")
        .description("Layout version of this config folder. Do not change it (it tells the plugin that this is the new folder layout).")
        .build();

    public static final Setting<List<String>> ENABLED_LANGUAGES = Setting.list("filters.enabled-word-lists", List.of("en", "custom"))
        .file(CONFIG).category(LANGUAGES).title("Active languages").overridable()
        .description("Which word-list files from the wordlists folder are switched on. Each language is one file named by its "
            + "code (en, de, id, pt-br ...). \"custom\" holds your own words (/abw addword) and is always on.\n"
            + "In game: /abw lang list, /abw lang enable <code>, /abw lang disable <code>.").build();

    public static final Setting<Integer> PROTECTION_LEVEL = Setting.integer("filters.protection-level", 3).file(CONFIG)
        .category(LANGUAGES).title("Protection level").range(1, 3).overridable()
        .description("How strict the filter is. Every list is split into level-1 (mild), level-2 (rude) and level-3 (severe) words.\n"
            + "  1 = LOW    -> only level-3 words are blocked\n"
            + "  2 = MEDIUM -> level-2 and level-3 words are blocked\n"
            + "  3 = HIGH   -> everything is blocked (level-1, 2 and 3)").build();

    public static final Setting<Boolean> SCAN_CHAT = scan("chat", "Scan chat", "Look for bad words in chat messages.");
    public static final Setting<Boolean> SCAN_COMMANDS = scan("commands", "Scan commands",
        "Look for bad words in commands such as /msg (see scanned-commands below).");
    public static final Setting<Boolean> SCAN_SIGNS = scan("signs", "Scan signs", "Look for bad words on signs.");
    public static final Setting<Boolean> SCAN_BOOKS = scan("books", "Scan books", "Look for bad words in books (pages and title).");
    public static final Setting<Boolean> SCAN_ANVIL = scan("anvil", "Scan anvil renames", "Look for bad words in item names typed in an anvil.");

    public static final Setting<Boolean> SCAN_SIGN_BACK = scan("sign-back", "Scan the back of signs",
        "Look at the back side of signs too (only used when scan.signs is true). Bukkit/Spigot/Paper/Purpur can tell the two\n"
            + "sides apart; on Fabric and Forge both sides are always covered by scan.signs and this switch changes nothing.");
    public static final Setting<Boolean> SCAN_COMMAND_BLOCKS = Setting.bool("filters.scan.command-blocks", false)
        .file(CONFIG).category(SCANNING).title("Scan command blocks").overridable()
        .description("Look at the commands that command blocks (and command-block minecarts) run, using scanned-commands below. "
            + "A command with a blocked word is not run. Bukkit/Spigot/Paper/Purpur only; not available on Fabric and Forge yet. "
            + "Nobody gets strikes for it (the game does not say who wrote the command).").build();
    public static final Setting<Boolean> SCAN_JOIN = Setting.bool("filters.scan.join-names", false)
        .file(CONFIG).category(SCANNING).title("Scan player names on join").overridable()
        .description("Check the name of every player who joins against the word lists (see join-names below for what happens).").build();

    /** The list version 1 shipped. Old installs keep exactly this list when they are migrated. */
    public static final List<String> LEGACY_SCANNED_COMMANDS =
        List.of("msg", "tell", "w", "whisper", "r", "reply", "me", "say", "teammsg", "tm", "mail", "nick", "broadcast");

    public static final Setting<List<String>> SCANNED_COMMANDS = Setting.list("filters.scanned-commands",
            List.of("msg", "tell", "w", "whisper", "r", "reply", "me", "say", "teammsg", "tm", "mail", "nick", "broadcast",
                "team", "title", "tellraw", "bossbar", "scoreboard", "rename"))
        .file(CONFIG).category(SCANNING).title("Scanned commands").overridable()
        .description("Commands whose arguments are scanned (only used when scan.commands is true).\n"
            + "Use \"*\" to scan the arguments of every command.").build();

    public static final Setting<List<String>> IGNORED_COMMANDS = Setting.list("filters.ignored-commands",
            List.of("antibadwords", "abw", "antiwords"))
        .file(CONFIG).category(SCANNING).title("Ignored commands").overridable()
        .description("Commands that are never scanned (so admins can still manage the word list).").build();

    public static final Setting<List<String>> CHECK_ORDER = Setting.list("filters.check-order", List.of("evasion", "badword", "link", "spam"))
        .file(CONFIG).category(SCANNING).title("Check order").overridable()
        .description("The order in which the checks look at a message. Checks that are not listed run afterwards.\n"
            + "The first check that blocks a message ends the search; a check that cleans a message hands the cleaned text on.\n"
            + "Names: evasion (bypass tricks, filters/evasion.yml), badword (the word lists), link (filters/links.yml),\n"
            + "spam (all rules of filters/spam.yml) or single spam rules: spam-commands, spam-rate, spam-repeat, spam-caps,\n"
            + "spam-flood, spam-words.").build();

    private static final String JOIN = "Player names";
    public static final Setting<String> JOIN_ACTION = Setting.choice("filters.join-names.action", "log", "log", "warn", "kick")
        .file(CONFIG).category(JOIN).title("Bad player name: action").overridable()
        .description("What happens when a joining player's name contains a blocked word (only used when scan.join-names is true):\n"
            + "  log  = a line in the console (and an event for the history / Discord features)\n"
            + "  warn = also tell the player and the staff\n"
            + "  kick = remove the player from the server with kick-message, and tell the staff\n"
            + "Players with the permission antibadwords.bypass are never checked. Nobody gets strikes for a name.").build();
    public static final Setting<Boolean> JOIN_MATCH_INSIDE = Setting.bool("filters.join-names.match-inside-names", true)
        .file(CONFIG).category(JOIN).title("Bad player name: look inside the name").overridable()
        .description("Player names have no spaces, so a blocked word is also found INSIDE a name (\"xXbadwordXx\").\n"
            + "Whitelisted words inside a name are skipped (\"Dickens99\" is fine while \"dickens\" is on the whitelist).\n"
            + "false = only when the whole name (or a part between _) is the word.").build();
    public static final Setting<Integer> JOIN_MIN_LENGTH = Setting.integer("filters.join-names.min-word-length", 4).range(3, 32)
        .file(CONFIG).category(JOIN).title("Bad player name: shortest word").overridable()
        .description("Blocked words shorter than this are not searched inside names (short words are part of many innocent names).").build();
    public static final Setting<String> JOIN_KICK_MESSAGE = Setting.string("filters.join-names.kick-message",
            "&cYour player name is not allowed on this server.")
        .file(CONFIG).category(JOIN).title("Bad player name: kick message").overridable()
        .description("Shown to the kicked player. Placeholders: {player} {word}. Colours: & codes.").build();
    public static final Setting<String> JOIN_WARN_MESSAGE = Setting.string("filters.join-names.warn-message",
            "&eYour player name contains a word that is not welcome here.")
        .file(CONFIG).category(JOIN).title("Bad player name: warning").overridable()
        .description("Sent to the player when the action is warn. Placeholders: {player} {word}. Empty = no message.").build();
    public static final Setting<String> JOIN_STAFF_MESSAGE = Setting.string("filters.join-names.staff-message",
            "&8[&cAntiBadWords&8] &7The name of &f{player}&7 contains '&f{word}&7'.")
        .file(CONFIG).category(JOIN).title("Bad player name: staff message").overridable()
        .description("Sent to staff (permission antibadwords.notify) for warn and kick, when messages.notify-staff is true.").build();

    public static final Setting<java.time.Duration> COMMAND_BLOCK_REPORT_INTERVAL = Setting.duration("filters.command-blocks.report-interval",
            java.time.Duration.ofSeconds(60)).range(0, 86_400)
        .file(CONFIG).category(SCANNING).title("Command blocks: report at most every")
        .description("A repeating command block can run 20 times per second. The same blocked command is reported (console, events) "
            + "only once in this time; it is stopped every time.").build();

    public static final Setting<Boolean> COLLAPSE_REPEATS = Setting.bool("filters.matching.collapse-repeated-letters", true)
        .file(CONFIG).category(MATCHING).title("Collapse repeated letters").overridable()
        .description("\"fuuuuck\" -> \"fuck\". Letters repeated many times in a row are collapsed.").build();
    public static final Setting<Integer> REPEAT_THRESHOLD = Setting.integer("filters.matching.repeated-letter-threshold", 3)
        .file(CONFIG).category(MATCHING).title("Repeated-letter threshold").min(2).overridable()
        .description("Letters repeated this many times (or more) in a row are collapsed.").build();
    public static final Setting<Boolean> DETECT_SPACED = Setting.bool("filters.matching.detect-spaced-letters", true)
        .file(CONFIG).category(MATCHING).title("Detect spaced letters").overridable()
        .description("Catch \"f u c k\" and \"f.u.c.k\" (3 or more single letters in a row).").build();
    public static final Setting<Boolean> JOIN_ADJACENT = Setting.bool("filters.matching.join-adjacent-words", false)
        .file(CONFIG).category(MATCHING).title("Join adjacent words").overridable()
        .description("Also glue ADJACENT words together before matching (catches \"fu ck\", but also \"pen is\").\n"
            + "Off by default because it causes false positives.").build();
    public static final Setting<Boolean> PARTIAL_MATCH = Setting.bool("filters.matching.partial-match", false)
        .file(CONFIG).category(MATCHING).title("Partial match").overridable()
        .description("Block a word when it merely CONTAINS a blocked word (e.g. \"motherfuckers\").\n"
            + "Off by default because it flags innocent words such as \"class\", \"closer\" or \"scrap\".\n"
            + "If you turn it on, add the innocent words to the whitelist below.").build();
    public static final Setting<Integer> PARTIAL_MIN_LENGTH = Setting.integer("filters.matching.partial-min-length", 4)
        .file(CONFIG).category(MATCHING).title("Partial match: shortest word").min(3).overridable()
        .description("Blocked words shorter than this are never used for partial matching.").build();

    public static final Setting<List<String>> WHITELIST = Setting.list("filters.whitelist",
            List.of("class", "classic", "assassin", "assume", "assist", "bass", "glass", "grass", "pass", "mass", "closer", "scrap",
                "cockpit", "cocktail", "peacock", "computer", "computing", "dickens", "analysis", "therapist"))
        .file(CONFIG).category(WHITELIST_CATEGORY).title("Whitelist").overridable()
        .description("Words that must never be blocked (mainly useful together with partial-match).\n"
            + "In game: /abw whitelist add <word>, /abw whitelist remove <word>, /abw whitelist list.").build();

    public static final Setting<Boolean> BROADCAST_WARNINGS = Setting.bool("messages.broadcast-warnings", true)
        .file(CONFIG).category(MESSAGES).title("Broadcast warnings").overridable()
        .description("Placeholders in all messages (any capitalisation): {player} {word} {source} {count} {limit} {remaining} {punishments}\n"
            + "Show the warning to every player (true) or only to the player who swore (false).").build();
    public static final Setting<String> WARNING_BROADCAST = message("warning-broadcast-format", "Warning (everyone)",
        "Sent to everyone when a bad word is blocked.", "&C{PLAYER} SAID THE WORD &F{WORD}&C WHICH IS NOT ALLOWED.");
    public static final Setting<String> WARNING_PLAYER = message("warning-player", "Warning (player)",
        "Sent only to the player who swore (while they are not punished yet).",
        "&EWATCH YOUR WORDS. YOU ARE PUNISHED IF YOU SAY &F{REMAINING}&E BADWORDS AGAIN.");
    public static final Setting<String> PUNISHMENT_BROADCAST = message("punishment-broadcast-format", "Punishment (everyone)",
        "Sent to everyone when the punishment is applied.",
        "&C{PLAYER} SAID THE WORD &F{WORD}&C WHICH IS NOT ALLOWED. FOR THEIR ACTION THEY'LL BE PUNISHED WITH &F{PUNISHMENTS}&C.");
    public static final Setting<String> PUNISHMENT_PLAYER = message("punishment-player", "Punishment (player)",
        "Sent to the player when the punishment is applied.", "&CFOR YOUR BADWORDS, YOU ARE PUNISHED WITH &F{PUNISHMENTS}&C.");
    public static final Setting<String> PUNISHMENT_DISABLED_PLAYER = message("punishment-disabled-player", "Blocked, punishment off (player)",
        "Sent to the player when punishment.enabled is false but they reached the limit.",
        "&EYOUR MESSAGE WAS BLOCKED. PUNISHMENT IS CURRENTLY DISABLED.");
    public static final Setting<Boolean> NOTIFY_STAFF = Setting.bool("messages.notify-staff", true)
        .file(CONFIG).category(MESSAGES).title("Notify staff").overridable()
        .description("Tell staff (permission antibadwords.notify) about every blocked word.").build();
    public static final Setting<String> STAFF_NOTIFY_FORMAT = message("staff-notify-format", "Staff notification",
        "What staff see.", "&8[&cAntiBadWords&8] &7Blocked '&f{word}&7' from &f{player}&7 in &f{source}&7 (&f{count}&7/&f{limit}&7).");

    public static final Setting<Boolean> LOG_CONSOLE = Setting.bool("logging.console", true)
        .file(CONFIG).category(GENERAL).title("Log to console").overridable()
        .description("Print every blocked word to the server console.").build();

    public static final Setting<Integer> LIST_PAGE_SIZE = Setting.integer("commands.list-page-size", 10)
        .file(CONFIG).category(GENERAL).title("List page size").range(1, 100)
        .description("How many entries one page of a list command shows (for example /abw whitelist list).").build();

    public static final Setting<Boolean> PUNISHMENT_ENABLED = Setting.bool("punishment.enabled", true)
        .file(LADDER).category(PUNISHMENT).title("Punishment enabled").overridable()
        .description("Master switch. When false, bad words are still blocked but nobody is punished.").build();
    public static final Setting<Integer> VIOLATIONS_BEFORE_PUNISH = Setting.integer("punishment.violations-before-punish", 2)
        .file(LADDER).category(PUNISHMENT).title("Violations before punishment").min(1).overridable()
        .description("How many violations before punishing the player").build();
    public static final Setting<Integer> VIOLATIONS_EXPIRE_MINUTES = Setting.integer("punishment.violations-expire-minutes", 0)
        .file(LADDER).category(PUNISHMENT).title("Violations expire after (minutes)").min(0)
        .description("Violations older than this many minutes are forgotten (0 = never expire).").build();
    public static final Setting<Boolean> CLEAR_ON_QUIT = Setting.bool("punishment.clear-violations-on-quit", true)
        .file(LADDER).category(PUNISHMENT).title("Clear violations on quit")
        .description("Forget a player's violations when they leave the server.").build();
    public static final Setting<Boolean> FINAL_COMMAND_ENABLED = Setting.bool("punishment.final-command.enabled", false)
        .file(LADDER).category(PUNISHMENT).title("Final command enabled").overridable()
        .description("Run the command below after the punishment actions.").build();
    public static final Setting<String> FINAL_COMMAND = Setting.string("punishment.final-command.command", "")
        .file(LADDER).category(PUNISHMENT).title("Final command").overridable()
        .description("Runs from the console after the actions below. Supports {player}, {word}, {source}.\n"
            + "Example: \"lp user {player} permission settemp antibadwords.muted true 30s\"").build();

    public static final Section EFFECTS = actions("effects", "Potion effects",
        "Every action below is applied (when enabled: true) at the moment a player is punished.\n"
            + "You may add your own entries; the names (blindness, slowness ...) are free.");
    public static final Section ATTRIBUTES = actions("attributes", "Attribute changes",
        "Temporarily SETS an attribute to a value, then restores the original value automatically\n"
            + "(also when the player leaves, the server stops, or after a crash).");
    public static final Section SOUNDS = actions("sounds", "Sounds", "Sounds played to the punished player.");
    public static final Section COMMANDS = actions("commands", "Console commands", "Console commands run for the punished player.");

    private CoreSettings() {
    }

    private static Setting<Boolean> scan(String name, String title, String description) {
        return Setting.bool("filters.scan." + name, true).file(CONFIG).category(SCANNING).title(title).description(description)
            .overridable().build();
    }

    private static Setting<String> message(String name, String title, String description, String def) {
        return Setting.string("messages." + name, def).file(CONFIG).category(MESSAGES).title(title)
            .description(description + " Colours: & codes.").overridable().build();
    }

    private static Section actions(String name, String title, String description) {
        String body = Resources.text("/abw-defaults/punishment-" + name + ".yml");
        return new Section("punishment." + name, LADDER, PUNISHMENT, title,
            description + "\nThis part cannot be edited in the in-game menu; edit the file.", body == null ? "{}" : body, true);
    }

    public static void register(SettingsRegistry r) {
        r.fileHeader(CONFIG, "AntiBadWords - main settings\n"
            + "Reload with /abw reload (no restart needed).\n"
            + "Text may use & colour codes (&c = red, &e = yellow, &f = white, ...).\n"
            + "Other files in this folder: wordlists/ (one file per language), moderation/ladder.yml (punishments),\n"
            + "filters/spam.yml, filters/links.yml, filters/evasion.yml (extra filters), worlds.yml (different rules per world).\n"
            + "Every option is explained in 6-guides/CONFIGURATION.md.");
        r.fileHeader(LADDER, "AntiBadWords - punishments\n"
            + "What happens to a player who keeps using blocked words. Reload with /abw reload.");
        r.register(CONFIG_VERSION);
        r.register(ENABLED_LANGUAGES);
        r.register(PROTECTION_LEVEL);
        r.register(SCAN_CHAT);
        r.register(SCAN_COMMANDS);
        r.register(SCAN_SIGNS);
        r.register(SCAN_BOOKS);
        r.register(SCAN_ANVIL);
        r.register(SCAN_SIGN_BACK);
        r.register(SCAN_COMMAND_BLOCKS);
        r.register(SCAN_JOIN);
        r.register(SCANNED_COMMANDS);
        r.register(IGNORED_COMMANDS);
        r.register(CHECK_ORDER);
        r.register(JOIN_ACTION);
        r.register(JOIN_MATCH_INSIDE);
        r.register(JOIN_MIN_LENGTH);
        r.register(JOIN_KICK_MESSAGE);
        r.register(JOIN_WARN_MESSAGE);
        r.register(JOIN_STAFF_MESSAGE);
        r.register(COMMAND_BLOCK_REPORT_INTERVAL);
        r.register(COLLAPSE_REPEATS);
        r.register(REPEAT_THRESHOLD);
        r.register(DETECT_SPACED);
        r.register(JOIN_ADJACENT);
        r.register(PARTIAL_MATCH);
        r.register(PARTIAL_MIN_LENGTH);
        r.register(WHITELIST);
        r.register(BROADCAST_WARNINGS);
        r.register(WARNING_BROADCAST);
        r.register(WARNING_PLAYER);
        r.register(PUNISHMENT_BROADCAST);
        r.register(PUNISHMENT_PLAYER);
        r.register(PUNISHMENT_DISABLED_PLAYER);
        r.register(NOTIFY_STAFF);
        r.register(STAFF_NOTIFY_FORMAT);
        r.register(LOG_CONSOLE);
        r.register(LIST_PAGE_SIZE);
        r.register(PUNISHMENT_ENABLED);
        r.register(VIOLATIONS_BEFORE_PUNISH);
        r.register(VIOLATIONS_EXPIRE_MINUTES);
        r.register(CLEAR_ON_QUIT);
        r.register(FINAL_COMMAND_ENABLED);
        r.register(FINAL_COMMAND);
        r.register(EFFECTS);
        r.register(ATTRIBUTES);
        r.register(SOUNDS);
        r.register(COMMANDS);
    }
}
