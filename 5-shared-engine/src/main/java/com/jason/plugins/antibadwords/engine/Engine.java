package com.jason.plugins.antibadwords.engine;

import com.jason.plugins.antibadwords.engine.command.CommandRegistry;
import com.jason.plugins.antibadwords.engine.command.CoreCommands;
import com.jason.plugins.antibadwords.engine.command.LadderCommands;
import com.jason.plugins.antibadwords.engine.config.ConfigProblem;
import com.jason.plugins.antibadwords.engine.config.ConfigStore;
import com.jason.plugins.antibadwords.engine.config.CoreSettings;
import com.jason.plugins.antibadwords.engine.config.LegacyMigration;
import com.jason.plugins.antibadwords.engine.config.Section;
import com.jason.plugins.antibadwords.engine.config.Setting;
import com.jason.plugins.antibadwords.engine.config.SettingsRegistry;
import com.jason.plugins.antibadwords.engine.config.WordList;
import com.jason.plugins.antibadwords.engine.config.WordLists;
import com.jason.plugins.antibadwords.engine.config.WorldProfiles;
import com.jason.plugins.antibadwords.engine.config.YamlEditor;
import com.jason.plugins.antibadwords.engine.discord.DiscordService;
import com.jason.plugins.antibadwords.engine.integration.ExternalProfiles;
import com.jason.plugins.antibadwords.engine.integration.IntegrationSettings;
import com.jason.plugins.antibadwords.engine.integration.PermissionSource;
import com.jason.plugins.antibadwords.engine.integration.PlaceholderValues;
import com.jason.plugins.antibadwords.engine.discord.DiscordSettings;
import com.jason.plugins.antibadwords.engine.filters.EvasionCheck;
import com.jason.plugins.antibadwords.engine.filters.EvasionSettings;
import com.jason.plugins.antibadwords.engine.filters.LinkCheck;
import com.jason.plugins.antibadwords.engine.filters.LinkSettings;
import com.jason.plugins.antibadwords.engine.filters.SpamCheck;
import com.jason.plugins.antibadwords.engine.filters.SpamSettings;
import com.jason.plugins.antibadwords.engine.filters.SpamState;
import com.jason.plugins.antibadwords.engine.gui.prompt.PromptService;
import com.jason.plugins.antibadwords.engine.gui.prompt.PromptSettings;
import com.jason.plugins.antibadwords.engine.moderation.DataFile;
import com.jason.plugins.antibadwords.engine.moderation.DurationParser;
import com.jason.plugins.antibadwords.engine.moderation.LadderSettings;
import com.jason.plugins.antibadwords.engine.moderation.HistorySettings;
import com.jason.plugins.antibadwords.engine.moderation.HistoryStore;
import com.jason.plugins.antibadwords.engine.moderation.ModerationSettings;
import com.jason.plugins.antibadwords.engine.moderation.MuteCommands;
import com.jason.plugins.antibadwords.engine.moderation.MuteHandler;
import com.jason.plugins.antibadwords.engine.moderation.MuteService;
import com.jason.plugins.antibadwords.engine.moderation.MuteSettings;
import com.jason.plugins.antibadwords.engine.moderation.PersistentStrikeService;
import com.jason.plugins.antibadwords.engine.moderation.PlayerDirectory;
import com.jason.plugins.antibadwords.Placeholders;
import com.jason.plugins.antibadwords.WordMatcher;
import com.jason.plugins.antibadwords.mod.Names;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.LongSupplier;
import java.util.function.Predicate;

/**
 * The platform-neutral heart of AntiBadWords. The Bukkit plugin and the Fabric/Forge mods all create one engine and
 * then only do three things: build a {@link Message}, call {@link #inspect(Message)}, apply the {@link Verdict}.
 *
 * <p>Extension points for new features:
 * <ul>
 *   <li>settings: add a {@code static void register(SettingsRegistry r)} method and call it in {@link #registerBuiltIns()},</li>
 *   <li>checks: {@link #registerCheck(int, TextCheck)},</li>
 *   <li>commands: {@code commands().register(new MyCommand())} (one class per sub-command),</li>
 *   <li>events: {@code events().subscribe(...)},</li>
 *   <li>strikes: {@link #setStrikeService(StrikeService)},</li>
 *   <li>things to redo after every (re)load: {@link #addReloadListener(Runnable)}.</li>
 * </ul>
 */
public final class Engine {
    /** Permission that exempts a player from every check. */
    public static final String PERMISSION_BYPASS = "antibadwords.bypass";
    /** Permission for the admin sub-commands. */
    public static final String PERMISSION_ADMIN = "antibadwords.admin";
    /** Permission to receive staff notifications about blocked words. */
    public static final String PERMISSION_NOTIFY = "antibadwords.notify";
    /** Sub-folder for state that is never edited by hand. */
    public static final String DATA_FOLDER = "data";

    /** Everything that is replaced as a whole on a reload, so readers on other threads always see one consistent set. */
    private record State(ConfigStore.Snapshot snapshot, WordLists wordLists, WorldProfiles profiles,
                         Map<String, EffectiveSettings> byProfile, List<ConfigProblem> problems) {
    }

    private record RegisteredCheck(int order, TextCheck check) {
    }

    private final Platform platform;
    private final Path folder;
    private final LongSupplier clock;
    private final SettingsRegistry registry = new SettingsRegistry();
    private final ConfigStore store;
    private final EventBus events;
    private final CommandRegistry commands;
    private final ViolationHandler violations = new ViolationHandler(this);
    private final List<RegisteredCheck> checks = new CopyOnWriteArrayList<>();
    private final Map<String, List<Setting<Boolean>>> sourceSwitches = new ConcurrentHashMap<>();
    private final SpamState spamState = new SpamState();
    private final PromptService prompts;
    /** Bumped whenever a check is registered, so the cached per-profile order is rebuilt. */
    private volatile int checksVersion;
    /** "This was reported at ..." for things that would otherwise be reported many times per second (command blocks). */
    private final Map<String, Long> recentReports = new java.util.LinkedHashMap<>();
    private final List<Runnable> reloadListeners = new CopyOnWriteArrayList<>();
    private final Set<UUID> anvilFlagged = ConcurrentHashMap.newKeySet();
    private final PersistentStrikeService persistentStrikes;
    private final PlayerDirectory players;
    private final HistoryStore history;
    private final MuteService mutes;
    private final DiscordService discord;
    private final com.jason.plugins.antibadwords.engine.integrations.BanService bans = new com.jason.plugins.antibadwords.engine.integrations.BanService(this);
    private final com.jason.plugins.antibadwords.engine.integrations.BedrockNames bedrock = new com.jason.plugins.antibadwords.engine.integrations.BedrockNames(this);
    private final com.jason.plugins.antibadwords.engine.integrations.EssentialsLink essentials;
    private final com.jason.plugins.antibadwords.engine.integrations.PluginCommands pluginCommands = new com.jason.plugins.antibadwords.engine.integrations.PluginCommands(this);
    private volatile StrikeService strikes;
    private final MuteHandler noMutes;
    private volatile MuteHandler muteHandler;
    private volatile State state;
    private volatile PermissionSource permissionSource;
    private final PlaceholderValues placeholders = new PlaceholderValues(this);

    private Engine(Platform platform, Path folder, LongSupplier clock) {
        this.platform = platform;
        this.folder = folder;
        this.clock = clock;
        this.prompts = new PromptService(this, platform, clock);
        this.store = new ConfigStore(folder, registry);
        this.events = new EventBus(platform.logger());
        this.commands = new CommandRegistry(this);
        this.discord = new DiscordService(this);
        this.essentials = new com.jason.plugins.antibadwords.engine.integrations.EssentialsLink(this);
        // data files are written off the server thread, a moment after the change
        DataFile.Scheduler saver = (task, delay) -> platform.runSync(() -> platform.runSyncLater(() -> platform.runAsync(task), delay));
        Path data = folder.resolve(DATA_FOLDER);
        this.players = new PlayerDirectory(data.resolve(ModerationSettings.PLAYERS_FILE), clock,
            () -> value(ModerationSettings.SAVE_DELAY).toMillis(), () -> value(ModerationSettings.FORGET_PLAYERS_DAYS) * 86_400_000L,
            platform.logger(), saver);
        this.persistentStrikes = new PersistentStrikeService(data.resolve(ModerationSettings.STRIKES_FILE), clock, this::strikeOptions,
            platform.logger(), saver);
        this.strikes = persistentStrikes;
        this.history = new HistoryStore(data, clock, platform.logger(), this::historyOptions);
        events.subscribe(history::record);
        this.mutes = new MuteService(this, data.resolve(MuteSettings.DATA_FILE), saver);
        // stand-in until the mute feature installs itself with setMuteHandler
        this.noMutes = (player, name, millis, reason, actor) -> {
            platform.logger().warn("Cannot mute " + name + " (" + DurationParser.format(millis) + "): the built-in mute is not available. "
                + "Use a command action with the mute command of another plugin instead.");
            return false;
        };
        this.muteHandler = mutes;
        registerBuiltIns();
    }

    /** Creates the engine and loads (or creates, or migrates) the config folder. */
    public static Engine load(Platform platform, Path configDir) {
        return load(platform, configDir, System::currentTimeMillis);
    }

    /** As {@link #load(Platform, Path)} with a replaceable clock (tests). */
    public static Engine load(Platform platform, Path configDir, LongSupplier clock) {
        Engine engine = new Engine(platform, configDir, clock);
        engine.reload();
        engine.loadData();
        return engine;
    }

    /** The one place where built-in features plug in. Later features add their lines here. */
    private void registerBuiltIns() {
        registerSettings(registry);
        registerSource(Message.CHAT, CoreSettings.SCAN_CHAT);
        registerSource(Message.COMMAND, CoreSettings.SCAN_COMMANDS);
        registerSource(Message.SIGN, CoreSettings.SCAN_SIGNS);
        registerSource(Message.BOOK, CoreSettings.SCAN_BOOKS);
        registerSource(Message.ANVIL, CoreSettings.SCAN_ANVIL);
        registerSource(Message.SIGN_BACK, CoreSettings.SCAN_SIGNS, CoreSettings.SCAN_SIGN_BACK);
        registerSource(Message.COMMAND_BLOCK, CoreSettings.SCAN_COMMAND_BLOCKS);
        registerSource(Message.JOIN, CoreSettings.SCAN_JOIN);
        registerCheck(50, new EvasionCheck());
        registerCheck(100, new BadWordCheck());
        registerCheck(200, new LinkCheck());
        registerCheck(300, new SpamCheck(SpamCheck.Kind.COMMANDS, spamState, clock));
        registerCheck(310, new SpamCheck(SpamCheck.Kind.RATE, spamState, clock));
        registerCheck(320, new SpamCheck(SpamCheck.Kind.REPEAT, spamState, clock));
        registerCheck(330, new SpamCheck(SpamCheck.Kind.CAPS, spamState, clock));
        registerCheck(340, new SpamCheck(SpamCheck.Kind.FLOOD, spamState, clock));
        registerCheck(350, new SpamCheck(SpamCheck.Kind.WORDS, spamState, clock));
        CoreCommands.register(commands);
        LadderCommands.register(commands);
        commands.register(new com.jason.plugins.antibadwords.engine.command.HistoryCommand());
        MuteCommands.register(commands);
        commands.register(new com.jason.plugins.antibadwords.engine.command.GuiCommand());
        discord.install();
        essentials.install();
    }

    /**
     * Registers the settings of every built-in feature. New features add one line here; the generated default files,
     * {@code 6-guides/CONFIGURATION.md} and the GUI follow automatically.
     */
    public static void registerSettings(SettingsRegistry registry) {
        CoreSettings.register(registry);
        WorldProfiles.register(registry);
        SpamSettings.register(registry);
        LinkSettings.register(registry);
        EvasionSettings.register(registry);
        ModerationSettings.register(registry);
        LadderSettings.register(registry);
        HistorySettings.register(registry);
        MuteSettings.register(registry);
        DiscordSettings.register(registry);
        com.jason.plugins.antibadwords.engine.discord.DiscordSrvSettings.register(registry);
        com.jason.plugins.antibadwords.engine.integrations.IntegrationSettings.register(registry);
        com.jason.plugins.antibadwords.engine.integration.IntegrationSettings.register(registry);
        PromptSettings.register(registry);
        com.jason.plugins.antibadwords.engine.integrations.IntegrationsSettings.register(registry);
        com.jason.plugins.antibadwords.engine.integrations.PluginCommandSettings.register(registry);
        com.jason.plugins.antibadwords.engine.integrations.NameplateSettings.register(registry);
        com.jason.plugins.antibadwords.engine.integrations.ChatChannels.register(registry);
    }

    /** Reads the state files in {@code data/} (server start). */
    private void loadData() {
        players.load();
        mutes.load();
        if (keepsStrikes()) {
            int loaded = persistentStrikes.load();
            if (loaded > 0) {
                platform.logger().info("Loaded the strikes of " + loaded + " players from " + DATA_FOLDER + "/" + ModerationSettings.STRIKES_FILE + ".");
            }
        } else {
            persistentStrikes.discardFile(notKeepingReason());
        }
    }

    /**
     * Whether strikes are written to disk and read back: the admin wants it and strikes are not forgotten on quit
     * anyway (with clear-on-quit nobody who is offline has strikes, so there is nothing to keep).
     */
    private boolean keepsStrikes() {
        return value(ModerationSettings.SAVE_TO_DISK) && !settings().legacy().clearViolationsOnQuit();
    }

    private String notKeepingReason() {
        return settings().legacy().clearViolationsOnQuit() ? "punishment.clear-violations-on-quit is true"
            : ModerationSettings.SAVE_TO_DISK.key() + " is false";
    }

    private PersistentStrikeService.Options strikeOptions() {
        return new PersistentStrikeService.Options(settings().legacy().violationExpireMinutes() * 60_000L,
            value(ModerationSettings.DECAY_EVERY).toMillis(), value(ModerationSettings.DECAY_POINTS), value(ModerationSettings.MAX_POINTS),
            value(ModerationSettings.SAVE_DELAY).toMillis(), keepsStrikes());
    }

    // ------------------------------------------------------------------------------------------
    // Configuration
    // ------------------------------------------------------------------------------------------

    /**
     * (Re)reads the whole config folder: migrates an old config.yml, creates missing files, reads settings, word lists
     * and world profiles. Never throws. A file that cannot be read keeps its previous good content.
     *
     * @return what was wrong (also written to the log); empty when everything is fine
     */
    public synchronized List<ConfigProblem> reload() {
        EngineLog log = platform.logger();
        State previous = state;
        List<ConfigProblem> problems = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        try {
            Files.createDirectories(folder.resolve(DATA_FOLDER));
        } catch (IOException ex) {
            problems.add(new ConfigProblem(DATA_FOLDER, null, "cannot create the folder: " + ex.getMessage()));
        }
        LegacyMigration.migrateIfNeeded(folder, registry, clock.getAsLong(), log::info, log::warn);
        try {
            List<String> created = store.writeMissingDefaults();
            if (!created.isEmpty()) {
                log.info("Created config files: " + String.join(", ", created));
            }
        } catch (IOException | RuntimeException ex) {
            problems.add(new ConfigProblem(folder.toString(), null, "cannot write the default files: " + ex.getMessage()));
        }
        try {
            int copied = WordLists.copyBundled(folder.resolve(WordLists.FOLDER));
            if (copied > 0) {
                log.info("Copied " + copied + " word-list files into " + WordLists.FOLDER + "/.");
            }
        } catch (IOException | RuntimeException ex) {
            problems.add(new ConfigProblem(WordLists.FOLDER, null, "cannot write the word-list files: " + ex.getMessage()));
        }

        ConfigStore.Snapshot snapshot = store.load(previous == null ? null : previous.snapshot());
        problems.addAll(snapshot.problems());
        WordLists lists = WordLists.load(folder.resolve(WordLists.FOLDER), previous == null ? null : previous.wordLists(), problems);
        WorldProfiles profiles = WorldProfiles.parse(snapshot.section(WorldProfiles.PROFILES), snapshot.section(WorldProfiles.WORLDS),
            snapshot.get(WorldProfiles.DEFAULT_PROFILE), snapshot.get(WorldProfiles.USE_PERMISSIONS), registry, problems);

        for (String name : snapshot.get(CoreSettings.CHECK_ORDER)) {
            String wanted = name.trim().toLowerCase(Locale.ROOT);
            if (checks.stream().noneMatch(c -> inOrderEntry(c.check().id(), wanted))) {
                problems.add(new ConfigProblem(CoreSettings.CONFIG, CoreSettings.CHECK_ORDER.key(), "'" + name
                    + "' is not the name of a check; it is ignored. Known: " + String.join(", ", checkIds()) + "."));
            }
        }

        Map<String, EffectiveSettings> byProfile = new LinkedHashMap<>();
        for (String name : profiles.names()) {
            Map<String, Object> values = new HashMap<>(snapshot.values());
            Map<String, Map<String, Object>> sections = new HashMap<>(snapshot.sections());
            for (Map.Entry<String, Object> override : profiles.overrides(name).entrySet()) {
                if (registry.entry(override.getKey()) instanceof Section) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> section = (Map<String, Object>) override.getValue();
                    sections.put(override.getKey(), section);
                } else {
                    values.put(override.getKey(), override.getValue());
                }
            }
            String prefix = name.equals(profiles.defaultProfile()) ? "" : "[profile " + name + "] ";
            byProfile.put(name, new EffectiveSettings(name, values, sections, lists, w -> warnings.add(prefix + w)));
        }
        state = new State(snapshot, lists, profiles, byProfile, List.copyOf(problems));

        for (ConfigProblem problem : problems) {
            log.warn("Config problem in " + problem);
        }
        warnings.stream().distinct().forEach(log::warn);
        EffectiveSettings main = settings();
        long enabledActions = main.legacy().punishmentActions().stream().filter(a -> a.enabled()).count();
        log.info("Loaded " + main.matcher().size() + " blocked words from " + main.enabledLanguages().size() + " lists.");
        log.info("Loaded punishment actions: " + main.legacy().punishmentActions().size() + " total, " + enabledActions + " enabled.");
        for (EffectiveSettings each : byProfile.values()) {
            if (!each.ladder().isEmpty()) {
                log.info("Punishment ladder" + (byProfile.size() > 1 ? " of profile " + each.profile() : "") + ": "
                    + each.ladder().steps().size() + " steps (the first at " + each.ladder().firstThreshold() + " strikes).");
            }
        }
        log.info("Integrations: ban provider " + bans.provider() + ", Bedrock names " + (bedrock.enabled()
            ? "on (prefix '" + value(com.jason.plugins.antibadwords.engine.integrations.IntegrationsSettings.BEDROCK_PREFIX) + "'"
                + (bedrock.detectedPlugin() == null ? "" : ", found " + bedrock.detectedPlugin()) + ")" : "off") + ".");
        pluginCommands.logFound();
        log.info("Protection level: " + main.get(CoreSettings.PROTECTION_LEVEL) + " (1=LOW, 2=MEDIUM, 3=HIGH)");
        if (profiles.names().size() > 1) {
            log.info("World profiles: " + String.join(", ", profiles.names()) + " (default: " + profiles.defaultProfile() + ")");
        }
        if (main.legacy().punishmentEnabled() && enabledActions == 0 && main.ladder().isEmpty()) {
            log.warn("Punishment is enabled but no punishment action is enabled in " + CoreSettings.LADDER + ".");
        }
        ExternalProfiles.validate(snapshot.section(IntegrationSettings.GROUP_PROFILES), profiles.names(), log::warn);
        com.jason.plugins.antibadwords.engine.integrations.ChatChannels.validate(snapshot.section(com.jason.plugins.antibadwords.engine.integrations.ChatChannels.CHANNEL_PROFILES), profiles.names(), log::warn);
        for (Runnable listener : reloadListeners) {
            try {
                listener.run();
            } catch (RuntimeException ex) {
                log.warn("A reload listener failed: " + ex);
            }
        }
        return state.problems();
    }

    /** Problems found by the last (re)load. */
    public List<ConfigProblem> problems() {
        return state.problems();
    }

    /** The registry of every setting (source for config files, docs and the GUI). */
    public SettingsRegistry registry() {
        return registry;
    }

    /** Low-level access to the config folder (read values of the files, comment-preserving edits). */
    public ConfigStore config() {
        return store;
    }

    /** The settings of the default profile (what applies when no world rule or permission picks another profile). */
    public EffectiveSettings settings() {
        State s = state;
        return s.byProfile().get(s.profiles().defaultProfile());
    }

    /** The settings for a player in a world: resolves the worlds.yml profile. */
    public EffectiveSettings settings(String world, String dimension, Predicate<String> permissions) {
        return settings(null, world, dimension, permissions);
    }

    /**
     * The settings for a player in a world: resolves the worlds.yml profile, also by the permission plugin's meta value or group
     * (see {@link #setPermissionSource}) when {@code player} is known.
     */
    public EffectiveSettings settings(UUID player, String world, String dimension, Predicate<String> permissions) {
        return settings(player, world, dimension, permissions, "");
    }

    /** Same, also honouring the chat channel ({@code integrations.chatchannels.channel-profiles}); it ranks below a permission plugin's choice. */
    public EffectiveSettings settings(UUID player, String world, String dimension, Predicate<String> permissions, String channel) {
        State s = state;
        String external = externalProfile(s, player);
        if (external == null && channel != null && !channel.isEmpty()) {
            external = channelProfile(s, channel);
        }
        return s.byProfile().get(s.profiles().resolve(world, dimension, permissions, external));
    }

    private String channelProfile(State s, String channel) {
        ConfigStore.Snapshot snap = s.snapshot();
        if (!snap.get(com.jason.plugins.antibadwords.engine.integrations.ChatChannels.ENABLED)) {
            return null;
        }
        return com.jason.plugins.antibadwords.engine.integrations.ChatChannels.profileFor(snap.section(com.jason.plugins.antibadwords.engine.integrations.ChatChannels.CHANNEL_PROFILES), channel, s.profiles().names());
    }

    private String commandChannel(String commandLine) {
        ConfigStore.Snapshot snap = state.snapshot();
        if (!snap.get(com.jason.plugins.antibadwords.engine.integrations.ChatChannels.ENABLED)) {
            return "";
        }
        return com.jason.plugins.antibadwords.engine.integrations.ChatChannels.channelOfCommand(snap.section(com.jason.plugins.antibadwords.engine.integrations.ChatChannels.CHANNEL_COMMANDS), commandLine);
    }

    private String externalProfile(State s, UUID player) {
        PermissionSource source = permissionSource;
        if (source == null || player == null) {
            return null;
        }
        ConfigStore.Snapshot snap = s.snapshot();
        try {
            return ExternalProfiles.choose(source, player, snap.get(IntegrationSettings.LP_USE_META), snap.get(IntegrationSettings.LP_META_KEY),
                snap.get(IntegrationSettings.LP_PRIMARY_ONLY), snap.section(IntegrationSettings.GROUP_PROFILES), s.profiles().names());
        } catch (RuntimeException ex) {
            platform.logger().warn("The permission plugin could not tell the profile of a player: " + ex);
            return null;
        }
    }

    /** Installs (or removes, with {@code null}) the permission plugin hook: groups, meta and offline permission checks. */
    public void setPermissionSource(PermissionSource source) {
        this.permissionSource = source;
    }

    /** The installed permission plugin hook, or {@code null}. */
    public PermissionSource permissionSource() {
        return permissionSource;
    }

    /**
     * Permission check that also works for offline players: online players are asked through the platform, offline ones through
     * the permission plugin (when its offline checks are on). Never blocks; the future completes with {@code false} when unknown.
     */
    public CompletableFuture<Boolean> hasPermission(UUID player, String node) {
        if (isOnline(player)) {
            return CompletableFuture.completedFuture(platform.has(player, node));
        }
        PermissionSource source = permissionSource;
        if (source == null || !value(IntegrationSettings.LP_OFFLINE)) {
            return CompletableFuture.completedFuture(false);
        }
        try {
            return source.hasPermission(player, node).exceptionally(ex -> false);
        } catch (RuntimeException ex) {
            return CompletableFuture.completedFuture(false);
        }
    }

    /** The values behind the PlaceholderAPI expansion. */
    public PlaceholderValues placeholders() {
        return placeholders;
    }

    /** The settings of one named profile, or {@code null}. */
    public EffectiveSettings profile(String name) {
        return state.byProfile().get(name.toLowerCase(Locale.ROOT));
    }

    /** What one profile of worlds.yml changes (setting key -> value); empty for unknown names or the plain default. */
    public java.util.Map<String, Object> profileOverrides(String name) {
        return state.profiles().overrides(name.toLowerCase(Locale.ROOT));
    }

    /** Names of all profiles in worlds.yml (always contains {@code default}). */
    public Set<String> profileNames() {
        return state.profiles().names();
    }

    public WordLists wordLists() {
        return state.wordLists();
    }

    /** A deep copy of the raw content of a free-form section as written in the files (without profile overrides). */
    public Map<String, Object> rawSection(Section section) {
        return deepCopy(state.snapshot().section(section));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> deepCopy(Map<String, Object> map) {
        Map<String, Object> out = new LinkedHashMap<>();
        map.forEach((k, v) -> out.put(k, copyValue(v)));
        return out;
    }

    @SuppressWarnings("unchecked")
    private static Object copyValue(Object v) {
        if (v instanceof Map<?, ?> m) {
            return deepCopy((Map<String, Object>) m);
        }
        if (v instanceof List<?> l) {
            List<Object> out = new ArrayList<>();
            l.forEach(i -> out.add(copyValue(i)));
            return out;
        }
        return v;
    }

    /** The value of a setting as written in the files (without profile overrides). */
    public <T> T value(Setting<T> setting) {
        return state.snapshot().get(setting);
    }

    /**
     * Changes one setting: checks the value, writes it into its file (comments stay) and reloads.
     *
     * @throws IllegalArgumentException when the value is not allowed for this setting
     * @throws IOException              when the file cannot be written
     */
    public synchronized <T> void set(Setting<T> setting, T value) throws IOException {
        setting.require(setting.toYaml(value));
        store.set(setting, value);
        reload();
    }

    /** Appends one item to a list setting in its file (the other lines and comments stay) and reloads. */
    public synchronized void addToList(Setting<List<String>> setting, String item) throws IOException {
        List<String> wanted = new ArrayList<>(value(setting));
        wanted.add(item);
        store.edit(setting.file(), editor -> {
            if (editor.contains(setting.key())) {
                editor.listAdd(setting.key(), item);
            } else {
                editor.set(setting.key(), wanted);
            }
        });
        reload();
    }

    /**
     * Removes every item of a list setting that matches, in its file, and reloads.
     *
     * @return false when nothing matched (nothing is written then)
     */
    public synchronized boolean removeFromList(Setting<List<String>> setting, Predicate<String> match) throws IOException {
        List<String> current = value(setting);
        List<String> wanted = current.stream().filter(match.negate()).toList();
        if (wanted.size() == current.size()) {
            return false;
        }
        store.edit(setting.file(), editor -> {
            if (editor.contains(setting.key())) {
                current.stream().filter(match).forEach(item -> editor.listRemove(setting.key(), item));
            } else {
                editor.set(setting.key(), wanted);
            }
        });
        reload();
        return true;
    }

    /** Runs after every (re)load, on the thread that reloaded. */
    public void addReloadListener(Runnable listener) {
        reloadListeners.add(listener);
    }

    // ------------------------------------------------------------------------------------------
    // Checking text
    // ------------------------------------------------------------------------------------------

    /**
     * Adds a pipeline stage. The running order is: the names listed in {@code filters.check-order} (a name is a check
     * id, or a group: {@code spam} stands for every id that starts with {@code spam-}), then every other check by
     * ascending {@code order}. Built-in orders: evasion 50, badword 100, link 200, spam-* 300 to 350.
     */
    public void registerCheck(int order, TextCheck check) {
        checks.removeIf(c -> c.check().id().equals(check.id()));
        checks.add(new RegisteredCheck(order, check));
        checksVersion++;
    }

    /**
     * Tells the engine which on/off setting belongs to a text source ({@code chat} -> {@code filters.scan.chat}).
     * With several settings all of them must be on ({@code sign-back} needs scan.signs and scan.sign-back).
     */
    @SafeVarargs
    public final void registerSource(String source, Setting<Boolean>... scanSwitches) {
        sourceSwitches.put(source, List.of(scanSwitches));
    }

    /** Whether text from this source is looked at with these settings (sources without a switch always are). */
    public boolean scans(String source, EffectiveSettings settings) {
        for (Setting<Boolean> scanSwitch : sourceSwitches.getOrDefault(source, List.of())) {
            if (!settings.get(scanSwitch)) {
                return false;
            }
        }
        return true;
    }

    /** Ids of all registered checks, in built-in order. */
    public List<String> checkIds() {
        return checks.stream().sorted(Comparator.comparingInt(RegisteredCheck::order)).map(c -> c.check().id()).toList();
    }

    /** The registered check with this id, or {@code null}. */
    public TextCheck check(String id) {
        for (RegisteredCheck c : checks) {
            if (c.check().id().equals(id)) {
                return c.check();
            }
        }
        return null;
    }

    private static boolean inOrderEntry(String checkId, String entry) {
        String id = checkId.toLowerCase(Locale.ROOT);
        return id.equals(entry) || id.startsWith(entry + "-");
    }

    /** The registered checks in the order they run for these settings. */
    public List<TextCheck> checks(EffectiveSettings settings) {
        return settings.derived("engine.checks." + checksVersion, s -> {
            List<String> configured = s.get(CoreSettings.CHECK_ORDER).stream().map(e -> e.trim().toLowerCase(Locale.ROOT)).toList();
            List<RegisteredCheck> sorted = new ArrayList<>(checks);
            sorted.sort(Comparator.comparingInt((RegisteredCheck c) -> {
                for (int i = 0; i < configured.size(); i++) {
                    if (inOrderEntry(c.check().id(), configured.get(i))) {
                        return i;
                    }
                }
                return Integer.MAX_VALUE;
            }).thenComparingInt(RegisteredCheck::order));
            return sorted.stream().map(RegisteredCheck::check).toList();
        });
    }

    /** Builds a message for an online player, asking the platform for the world and the permissions. */
    public Message message(UUID playerId, String playerName, String source, String text) {
        return Message.of(playerId, playerName, platform.worldName(playerId), source, text, node -> platform.has(playerId, node))
            .withDimension(platform.dimensionName(playerId));
    }

    /**
     * Judges one message. May be called from any thread. It changes nothing a player can see; the only thing it
     * remembers are the spam counters (so call it once per message). When the verdict has hits, call
     * {@link #onViolation(Message, Verdict)} afterwards.
     *
     * <p>A "censor" result is turned into "block" where the loader cannot deliver a changed text
     * ({@link Platform#canRewrite(String)}).
     */
    public Verdict inspect(Message message) {
        if (Message.CHAT.equals(message.source()) && message.playerId() != null && prompts.onChat(message.playerId(), message.text())) {
            return Verdict.captured(settings());
        }
        if (mutes.enforce(message)) {
            // a muted player: stopped without hits, so no strikes are counted; the service has told the player
            return new Verdict(Verdict.Type.BLOCK, null, List.of(), settings(message.playerId(), message.world(), message.dimension(), message.permissions()));
        }
        return judge(message, true, true);
    }

    /**
     * What the checks would say about a text, for {@code /abw check}: default profile, as a chat message, no player.
     * Nothing is counted or remembered; the rules that need a history of earlier messages are skipped.
     */
    public Verdict explain(String text) {
        return judge(Message.of(null, "", "", Message.CHAT, text, null).asProbe(), false, false);
    }

    /**
     * @param useScanSwitch whether a source that is switched off in {@code filters.scan} is skipped
     * @param forDelivery   whether "censor" becomes "block" where the text cannot be changed
     */
    private Verdict judge(Message message, boolean useScanSwitch, boolean forDelivery) {
        if (message.channel().isEmpty() && Message.COMMAND.equals(message.source()) && message.text() != null) {
            String channel = commandChannel(message.text());
            if (!channel.isEmpty()) {
                message = message.withChannel(channel);
            }
        }
        EffectiveSettings settings = settings(message.playerId(), message.world(), message.dimension(), message.permissions(),
            message.channel());
        if (message.text() == null || message.has(PERMISSION_BYPASS)) {
            return Verdict.allow(settings);
        }
        boolean scanned = !useScanSwitch || scans(message.source(), settings);
        boolean playerCommand = Message.COMMAND.equals(message.source());
        // "current" is what the text checks look at; null = nothing for them (a command that is not scanned)
        Message current = scanned ? message : null;
        if (scanned && (playerCommand || Message.COMMAND_BLOCK.equals(message.source()))) {
            String arguments = commandTextToScan(settings, message.text());
            if (arguments == null) {
                arguments = essentials.extraCommandText(settings.legacy().ignoredCommands(), message.text());
            }
            if (arguments == null) {
                arguments = pluginCommands.extraCommandText(settings.legacy().ignoredCommands(), message.text());
            }
            if (arguments == null) {
                arguments = com.jason.plugins.antibadwords.engine.integrations.PluginCommandScan.extraCommandText(this,
                    settings.legacy().ignoredCommands(), message.text());
            }
            current = arguments == null ? null : message.withText(arguments);
        }
        if (current == null && !playerCommand) {
            return Verdict.allow(settings);
        }
        List<Hit> hits = null;
        boolean blocked = false;
        String censored = null;
        for (TextCheck check : checks(settings)) {
            // a check that counts commands sees every command line, whatever the scan settings say
            Message input = playerCommand && check.seesEveryCommand() ? message : current;
            if (input == null) {
                continue;
            }
            Hit hit;
            try {
                if (!check.enabled(settings)) {
                    continue;
                }
                hit = check.check(input, settings).orElse(null);
            } catch (RuntimeException ex) {
                platform.logger().warn("Check '" + check.id() + "' failed and was skipped for this message: " + ex);
                continue;
            }
            if (hit == null) {
                continue;
            }
            if (hits == null) {
                hits = new ArrayList<>();
            }
            hits.add(hit);
            if (hit.action() == Hit.Action.BLOCK) {
                blocked = true;
                break;
            }
            if (hit.action() == Hit.Action.CENSOR && hit.replacement() != null && input == current) {
                censored = hit.replacement();
                current = current.withText(censored);
            }
        }
        if (hits == null) {
            return Verdict.allow(settings);
        }
        Verdict.Type type = blocked ? Verdict.Type.BLOCK : censored != null ? Verdict.Type.CENSOR : Verdict.Type.ALLOW;
        if (type == Verdict.Type.CENSOR && forDelivery && (playerCommand || !platform.canRewrite(message.source()))) {
            type = Verdict.Type.BLOCK; // nobody can deliver the cleaned text here, so the message is stopped
        }
        return new Verdict(type, type == Verdict.Type.CENSOR ? censored : null, hits, settings);
    }

    /**
     * Judges several texts that belong together (sign lines, book pages) and returns the first verdict that is not a
     * plain "allow"; one violation is counted for the whole sign or book, like before.
     */
    public Verdict inspectFirst(Message template, Collection<String> texts) {
        Verdict last = null;
        for (String text : texts) {
            if (text == null) {
                continue;
            }
            last = inspect(template.withText(text));
            if (last.hasHits()) {
                return last;
            }
        }
        return last != null ? last : Verdict.allow(settings(template.playerId(), template.world(), template.dimension(), template.permissions()));
    }

    /**
     * For a command line such as {@code /msg Bob hello}: the text after the label that must be scanned, or {@code null}
     * when the command is not scanned (bare command, ignored command, or not in scanned-commands).
     */
    public static String commandTextToScan(EffectiveSettings settings, String commandLine) {
        int space = commandLine.indexOf(' ');
        if (space < 0) {
            return null; // a bare command has no arguments that could contain a bad word
        }
        String label = Names.commandLabel(commandLine.substring(0, space));
        if (settings.legacy().ignoredCommands().contains(label)) {
            return null; // e.g. /abw addword <word> must keep working for admins
        }
        if (!settings.legacy().scannedCommands().contains("*") && !settings.legacy().scannedCommands().contains(label)) {
            return null;
        }
        return commandLine.substring(space + 1);
    }

    /**
     * Follow-up for a verdict with hits: counts strikes, notifies staff, warns or punishes and publishes events.
     * Safe to call from any thread: the work is moved to the server's main thread.
     */
    public void onViolation(Message message, Verdict verdict) {
        if (verdict.hasHits()) {
            platform.runSync(() -> violations.handle(message, verdict));
        }
    }

    // ------------------------------------------------------------------------------------------
    // Sources that are not "a player typed text": join names and command blocks
    // ------------------------------------------------------------------------------------------

    /**
     * Call when a player has joined (main thread): checks the player's name when {@code filters.scan.join-names} is
     * on and does what {@code filters.join-names.action} says (log, warn or kick). No strikes are counted.
     *
     * @return the verdict about the name (plain "allow" when the check is off or the name is fine)
     */
    public Verdict onJoin(UUID playerId, String playerName) {
        players.seen(playerId, playerName);
        mutes.onJoin(playerId, playerName);
        platform.runSyncLater(() -> essentials.onJoin(playerId, playerName), 1000); // Essentials loads the user data while the join runs
        Message message = message(playerId, playerName, Message.JOIN, bedrock.filterName(playerName));
        Verdict verdict = judge(message, true, false);
        Hit hit = verdict.primary();
        if (hit == null || verdict.allowed()) {
            return verdict; // fine, or only noted by a log-only check
        }
        EffectiveSettings settings = verdict.settings();
        String action = settings.get(CoreSettings.JOIN_ACTION);
        Map<String, String> values = new HashMap<>();
        values.put("player", playerName);
        values.put("word", hit.detail() == null ? "" : hit.detail());
        values.put("source", Message.JOIN);
        if (settings.legacy().logToConsole()) {
            platform.logger().info(playerName + " joined with a player name that is not allowed (" + hit.check() + ": '" + hit.detail()
                + "'), action: " + action);
        }
        Map<String, String> data = new HashMap<>(values);
        data.put("check", hit.check());
        data.put("detail", values.get("word"));
        data.put("severity", Integer.toString(hit.severity()));
        data.put("action", action);
        data.put("weight", "0");
        data.put("profile", settings.profile());
        data.put("text", playerName);
        events.publish(new ModerationEvent(ModerationEvent.VIOLATION, now(), playerId, playerName, message.world(), Message.JOIN, data));
        if (action.equals("log")) {
            return verdict;
        }
        if (settings.legacy().notifyStaff()) {
            String staff = Placeholders.apply(Names.colour(settings.get(CoreSettings.JOIN_STAFF_MESSAGE)), values);
            for (PlayerRef other : platform.onlinePlayers()) {
                if (!other.id().equals(playerId) && !staff.isBlank() && platform.has(other.id(), PERMISSION_NOTIFY)) {
                    platform.sendMessage(other.id(), staff);
                }
            }
        }
        if (action.equals("kick")) {
            String reason = Placeholders.apply(Names.colour(settings.get(CoreSettings.JOIN_KICK_MESSAGE)), values);
            // a moment later: some servers do not like a kick in the middle of the join
            platform.runSyncLater(() -> platform.kick(playerId, reason), 100L);
        } else {
            String warning = Placeholders.apply(Names.colour(settings.get(CoreSettings.JOIN_WARN_MESSAGE)), values);
            if (!warning.isBlank()) {
                platform.sendMessage(playerId, warning);
            }
        }
        return verdict;
    }

    /**
     * Call when a command block (or command-block minecart) is about to run a command. Uses
     * {@code filters.scan.command-blocks} and {@code scanned-commands}. Nobody gets strikes; the same command is
     * reported at most once per {@code filters.command-blocks.report-interval}.
     *
     * @param world where the block is (for worlds.yml profiles), may be ""
     * @param where short description for the log, for example {@code world 10,64,-3}
     * @return true when the command must not run
     */
    public boolean blockCommandBlock(String world, String where, String commandLine) {
        Message message = Message.of(null, "CommandBlock(" + where + ")", world, Message.COMMAND_BLOCK, commandLine, null);
        Verdict verdict = inspect(message);
        if (verdict.hasHits() && firstReport(Message.COMMAND_BLOCK + "|" + where + "|" + commandLine,
            verdict.settings().get(CoreSettings.COMMAND_BLOCK_REPORT_INTERVAL).toMillis())) {
            onViolation(message, verdict);
        }
        return verdict.blocked() || verdict.censored();
    }

    /** True when {@code key} was not reported within the last {@code quietMillis} (and notes that it is reported now). */
    private boolean firstReport(String key, long quietMillis) {
        long now = now();
        synchronized (recentReports) {
            Long last = recentReports.get(key);
            if (last != null && now >= last && now - last < quietMillis) {
                return false;
            }
            recentReports.remove(key);
            recentReports.put(key, now);
            while (recentReports.size() > 256) {
                recentReports.remove(recentReports.keySet().iterator().next());
            }
            return true;
        }
    }

    /** What the spam checks remember about players (for tests and status displays). */
    public SpamState spamState() {
        return spamState;
    }

    /** First blocked word in the text for the default profile, or {@code null} ({@code /abw check}). */
    public String find(String text) {
        return settings().matcher().find(text);
    }

    // ------------------------------------------------------------------------------------------
    // Services
    // ------------------------------------------------------------------------------------------

    /** The text prompts of the menu (chat capture, sign input). */
    public PromptService prompts() {
        return prompts;
    }

    public Platform platform() {
        return platform;
    }

    /** The ladder actions tempban and ipban (settings {@code integrations.bans.*}). */
    public com.jason.plugins.antibadwords.engine.integrations.BanService bans() {
        return bans;
    }

    /** Bedrock (Geyser / Floodgate) name handling (settings {@code integrations.bedrock.*}). */
    public com.jason.plugins.antibadwords.engine.integrations.BedrockNames bedrock() {
        return bedrock;
    }

    /** Text commands of mcMMO, Towny, Factions, Citizens, DecentHolograms (settings {@code integrations.<plugin>.*}). */
    public com.jason.plugins.antibadwords.engine.integrations.PluginCommands pluginCommands() {
        return pluginCommands;
    }

    /** EssentialsX hook (Bukkit family; settings in {@code integrations.yml}). Does nothing until the plugin attaches a bridge. */
    public com.jason.plugins.antibadwords.engine.integrations.EssentialsLink essentials() {
        return essentials;
    }

    /**
     * Judges a name-like text (a nickname) with all word checks, like a join name: no scan switch, nothing counted.
     * Does not change anything; the caller decides what to do.
     */
    public Verdict inspectName(UUID playerId, String playerName, String text) {
        return judge(message(playerId, playerName, Message.JOIN, text), false, false);
    }

    /** Discord webhook alerts (settings in {@code discord.yml}, command {@code /abw discord}). */
    public DiscordService discord() {
        return discord;
    }

    public EventBus events() {
        return events;
    }

    public CommandRegistry commands() {
        return commands;
    }

    public StrikeService strikes() {
        return strikes;
    }

    /** The moderation history ({@code data/history}); fed by the event stream, read by {@code /abw history}. */
    public HistoryStore history() {
        return history;
    }

    private HistoryStore.Options historyOptions() {
        java.util.Set<String> kinds = new java.util.HashSet<>();
        value(HistorySettings.KINDS).forEach(k -> kinds.add(k.trim().toLowerCase(java.util.Locale.ROOT)));
        return new HistoryStore.Options(value(HistorySettings.ENABLED), kinds, value(HistorySettings.STORE_TEXT),
            value(HistorySettings.REDACT_WORDS), value(HistorySettings.RETENTION_DAYS), value(HistorySettings.MAX_TOTAL_MB) * 1_048_576L,
            value(HistorySettings.PAGE_SIZE));
    }

    /** The built-in mute (state in {@code data/mutes.json}); also installed as {@link #muteHandler()}. */
    public MuteService mutes() {
        return mutes;
    }

    /** Everybody who was ever seen on the server ({@code data/players.json}); finds offline players by name. */
    public PlayerDirectory players() {
        return players;
    }

    /**
     * Finds a player by name: first among the online players (exact name), then among everybody who was seen before.
     * Empty when the name is unknown.
     */
    public Optional<PlayerRef> resolvePlayer(String name) {
        Optional<PlayerRef> online = platform.findPlayer(name);
        if (online.isPresent()) {
            return online;
        }
        Optional<PlayerRef> known = players.find(name).map(k -> new PlayerRef(k.id(), k.name()));
        if (known.isPresent() || !bedrock.enabled()) {
            return known;
        }
        // Bedrock names: with/without the prefix, space or underscore
        for (String variant : bedrock.variants(name)) {
            online = platform.findPlayer(variant);
            if (online.isPresent()) {
                return online;
            }
            known = players.find(variant).map(k -> new PlayerRef(k.id(), k.name()));
            if (known.isPresent()) {
                return known;
            }
        }
        return Optional.empty();
    }

    /** Whether that player is on the server right now. */
    public boolean isOnline(UUID player) {
        return platform.onlinePlayers().stream().anyMatch(p -> p.id().equals(player));
    }

    /**
     * Replaces the strike counter. The built-in one is a {@link PersistentStrikeService} (points per UUID, expiry,
     * decay, cap, {@code data/strikes.json}).
     */
    public void setStrikeService(StrikeService service) {
        this.strikes = service;
    }

    /** Who carries out the ladder's {@code mute} action. Until the mute feature is installed this only logs a warning. */
    public MuteHandler muteHandler() {
        return muteHandler;
    }

    /** Installs the mute feature for the ladder's {@code mute} action; {@code null} puts the logging stand-in back. */
    public void setMuteHandler(MuteHandler handler) {
        this.muteHandler = handler == null ? noMutes : handler;
    }

    /** Current time in milliseconds (replaceable in tests). */
    public long now() {
        return clock.getAsLong();
    }

    public String version() {
        return platform.pluginVersion();
    }

    /** Publishes an {@code admin-command} event. {@code senderId} is {@code null} for the console. */
    public void publishAdminCommand(UUID senderId, String senderName, String commandLine) {
        events.publish(new ModerationEvent(ModerationEvent.ADMIN_COMMAND, now(), senderId, senderName, "", "command",
            Map.of("command", commandLine, "sender", senderName)));
    }

    // ------------------------------------------------------------------------------------------
    // Player housekeeping
    // ------------------------------------------------------------------------------------------

    /**
     * Anvils re-check the name on every keystroke: returns true only the first time per anvil session, so one
     * violation is counted while the result slot stays empty.
     */
    public boolean firstAnvilViolation(UUID player) {
        return anvilFlagged.add(player);
    }

    public void anvilClosed(UUID player) {
        anvilFlagged.remove(player);
    }

    /** Call when a player leaves. */
    public void onQuit(UUID player) {
        anvilFlagged.remove(player);
        spamState.forget(player);
        players.touch(player);
        if (settings().legacy().clearViolationsOnQuit()) {
            strikes.reset(player);
        }
    }

    private volatile boolean shutDown;

    /** Call when the server stops. */
    public void shutdown() {
        if (shutDown) {
            return;
        }
        shutDown = true;
        discord.shutdown();
        // strikes that are kept across restarts are written first; memory is emptied either way
        boolean keep = keepsStrikes();
        persistentStrikes.shutdown(keep);
        if (!keep) {
            persistentStrikes.discardFile(notKeepingReason()); // a file from an earlier run must not come back after a later switch
        }
        if (strikes != persistentStrikes) {
            strikes.clear();
        }
        players.flush();
        history.shutdown();
        mutes.flush();
        anvilFlagged.clear();
        spamState.clear();
    }

    // ------------------------------------------------------------------------------------------
    // Word lists
    // ------------------------------------------------------------------------------------------

    /** The admin's own words (all tiers of wordlists/custom.yml). */
    public List<String> customWords() {
        WordList custom = wordLists().get(WordLists.CUSTOM);
        List<String> out = new ArrayList<>();
        if (custom != null) {
            custom.tiers().forEach(out::addAll);
        }
        return out;
    }

    /** Adds a word to wordlists/custom.yml (level-3, so it counts at every protection level). @return false when invalid or present */
    public synchronized boolean addCustomWord(String rawWord) {
        if (!WordMatcher.isValidEntry(rawWord)) {
            return false;
        }
        String word = WordMatcher.cleanEntry(rawWord);
        if (customWords().contains(word)) {
            return false;
        }
        return editCustom(editor -> editor.listAdd("level-3", word));
    }

    public synchronized boolean removeCustomWord(String rawWord) {
        String word = WordMatcher.cleanEntry(rawWord);
        if (!customWords().contains(word)) {
            return false;
        }
        return editCustom(editor -> {
            for (int tier = 1; tier <= 3; tier++) {
                editor.listRemove("level-" + tier, word);
            }
        });
    }

    private boolean editCustom(java.util.function.Consumer<YamlEditor> change) {
        try {
            WordLists.copyBundled(folder.resolve(WordLists.FOLDER));
            store.edit(WordLists.FOLDER + "/" + WordLists.CUSTOM + ".yml", change);
        } catch (IOException | RuntimeException ex) {
            platform.logger().warn("Could not save the custom word list: " + ex);
            return false;
        } finally {
            reload();
        }
        return true;
    }

    /** Words of a language code at the current protection level, or of {@code custom} / {@code active}. */
    public List<String> listWords(String listName) {
        String key = WordLists.canonicalCode(listName);
        if (key.equals(WordLists.CUSTOM)) {
            return customWords();
        }
        if (key.equals("active")) {
            return settings().activeWords();
        }
        WordList list = wordLists().get(key);
        return list == null ? List.of() : new ArrayList<>(new java.util.LinkedHashSet<>(list.words(settings().get(CoreSettings.PROTECTION_LEVEL))));
    }

    /** Every language code, then {@code custom} and {@code active}. */
    public List<String> availableLists() {
        List<String> lists = new ArrayList<>(wordLists().codes());
        lists.remove(WordLists.CUSTOM);
        lists.add(WordLists.CUSTOM);
        lists.add("active");
        return lists;
    }
}
