package com.jason.plugins.antibadwords.engine;

import com.jason.plugins.antibadwords.engine.command.CommandRegistry;
import com.jason.plugins.antibadwords.engine.command.CoreCommands;
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
import java.util.Set;
import java.util.UUID;
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
    private final Map<String, Setting<Boolean>> sourceSwitches = new ConcurrentHashMap<>();
    private final List<Runnable> reloadListeners = new CopyOnWriteArrayList<>();
    private final Set<UUID> anvilFlagged = ConcurrentHashMap.newKeySet();
    private volatile StrikeService strikes;
    private volatile State state;

    private Engine(Platform platform, Path folder, LongSupplier clock) {
        this.platform = platform;
        this.folder = folder;
        this.clock = clock;
        this.store = new ConfigStore(folder, registry);
        this.events = new EventBus(platform.logger());
        this.commands = new CommandRegistry(this);
        this.strikes = new InMemoryStrikeService(clock, () -> settings().legacy().violationExpireMinutes() * 60_000L);
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
        registerCheck(100, new BadWordCheck());
        CoreCommands.register(commands);
    }

    /**
     * Registers the settings of every built-in feature. New features add one line here; the generated default files,
     * {@code 6-guides/CONFIGURATION.md} and the GUI follow automatically.
     */
    public static void registerSettings(SettingsRegistry registry) {
        CoreSettings.register(registry);
        WorldProfiles.register(registry);
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
        log.info("Protection level: " + main.get(CoreSettings.PROTECTION_LEVEL) + " (1=LOW, 2=MEDIUM, 3=HIGH)");
        if (profiles.names().size() > 1) {
            log.info("World profiles: " + String.join(", ", profiles.names()) + " (default: " + profiles.defaultProfile() + ")");
        }
        if (main.legacy().punishmentEnabled() && enabledActions == 0) {
            log.warn("Punishment is enabled but no punishment action is enabled in " + CoreSettings.LADDER + ".");
        }
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
        State s = state;
        return s.byProfile().get(s.profiles().resolve(world, dimension, permissions));
    }

    /** The settings of one named profile, or {@code null}. */
    public EffectiveSettings profile(String name) {
        return state.byProfile().get(name.toLowerCase(Locale.ROOT));
    }

    /** Names of all profiles in worlds.yml (always contains {@code default}). */
    public Set<String> profileNames() {
        return state.profiles().names();
    }

    public WordLists wordLists() {
        return state.wordLists();
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
     * Adds a pipeline stage. The running order is: the ids listed in {@code filters.check-order}, then every other
     * check by ascending {@code order} (the built-in {@code badword} check has order 100).
     */
    public void registerCheck(int order, TextCheck check) {
        checks.removeIf(c -> c.check().id().equals(check.id()));
        checks.add(new RegisteredCheck(order, check));
    }

    /** Tells the engine which on/off setting belongs to a text source ({@code chat} -> {@code filters.scan.chat}). */
    public void registerSource(String source, Setting<Boolean> scanSwitch) {
        sourceSwitches.put(source, scanSwitch);
    }

    /** The registered checks in the order they run for these settings. */
    public List<TextCheck> checks(EffectiveSettings settings) {
        List<String> configured = settings.get(CoreSettings.CHECK_ORDER).stream().map(s -> s.trim().toLowerCase(Locale.ROOT)).toList();
        List<RegisteredCheck> sorted = new ArrayList<>(checks);
        sorted.sort(Comparator.comparingInt((RegisteredCheck c) -> {
            int index = configured.indexOf(c.check().id().toLowerCase(Locale.ROOT));
            return index < 0 ? Integer.MAX_VALUE : index;
        }).thenComparingInt(RegisteredCheck::order));
        return sorted.stream().map(RegisteredCheck::check).toList();
    }

    /** Builds a message for an online player, asking the platform for the world and the permissions. */
    public Message message(UUID playerId, String playerName, String source, String text) {
        return Message.of(playerId, playerName, platform.worldName(playerId), source, text, node -> platform.has(playerId, node))
            .withDimension(platform.dimensionName(playerId));
    }

    /**
     * Judges one message. Has no side effects and may be called from any thread. When the verdict has hits, call
     * {@link #onViolation(Message, Verdict)} afterwards.
     */
    public Verdict inspect(Message message) {
        EffectiveSettings settings = settings(message.world(), message.dimension(), message.permissions());
        if (message.text() == null || message.has(PERMISSION_BYPASS)) {
            return Verdict.allow(settings);
        }
        Setting<Boolean> scanSwitch = sourceSwitches.get(message.source());
        if (scanSwitch != null && !settings.get(scanSwitch)) {
            return Verdict.allow(settings);
        }
        Message current = message;
        if (Message.COMMAND.equals(message.source())) {
            String arguments = commandTextToScan(settings, message.text());
            if (arguments == null) {
                return Verdict.allow(settings);
            }
            current = message.withText(arguments);
        }
        List<Hit> hits = new ArrayList<>();
        boolean blocked = false;
        String censored = null;
        for (TextCheck check : checks(settings)) {
            Hit hit;
            try {
                if (!check.enabled(settings)) {
                    continue;
                }
                hit = check.check(current, settings).orElse(null);
            } catch (RuntimeException ex) {
                platform.logger().warn("Check '" + check.id() + "' failed and was skipped for this message: " + ex);
                continue;
            }
            if (hit == null) {
                continue;
            }
            hits.add(hit);
            if (hit.action() == Hit.Action.BLOCK) {
                blocked = true;
                break;
            }
            if (hit.action() == Hit.Action.CENSOR && hit.replacement() != null) {
                censored = hit.replacement();
                current = current.withText(censored);
            }
        }
        Verdict.Type type = blocked ? Verdict.Type.BLOCK : censored != null ? Verdict.Type.CENSOR : Verdict.Type.ALLOW;
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
        return last != null ? last : Verdict.allow(settings(template.world(), template.dimension(), template.permissions()));
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

    /** First blocked word in the text for the default profile, or {@code null} ({@code /abw check}). */
    public String find(String text) {
        return settings().matcher().find(text);
    }

    // ------------------------------------------------------------------------------------------
    // Services
    // ------------------------------------------------------------------------------------------

    public Platform platform() {
        return platform;
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

    /** Replaces the strike counter (for example with a persistent one). */
    public void setStrikeService(StrikeService service) {
        this.strikes = service;
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
        if (settings().legacy().clearViolationsOnQuit()) {
            strikes.reset(player);
        }
    }

    /** Call when the server stops. */
    public void shutdown() {
        strikes.clear();
        anvilFlagged.clear();
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
