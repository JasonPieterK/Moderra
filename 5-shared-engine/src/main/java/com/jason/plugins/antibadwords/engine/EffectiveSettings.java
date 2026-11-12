package com.jason.plugins.antibadwords.engine;

import com.jason.plugins.antibadwords.AntiBadWordsSettings;
import com.jason.plugins.antibadwords.PunishmentAction;
import com.jason.plugins.antibadwords.WordMatcher;
import com.jason.plugins.antibadwords.engine.config.CoreSettings;
import com.jason.plugins.antibadwords.engine.config.Section;
import com.jason.plugins.antibadwords.engine.config.Setting;
import com.jason.plugins.antibadwords.engine.config.PunishmentParser;
import com.jason.plugins.antibadwords.engine.config.WordList;
import com.jason.plugins.antibadwords.engine.config.WordLists;
import com.jason.plugins.antibadwords.mod.Names;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The settings that apply to one message: the values from the files with the overrides of the chosen
 * {@code worlds.yml} profile already applied. Immutable; the engine builds one per profile on every (re)load and
 * resolves the right one once per message.
 *
 * <p>Checks read their settings with {@link #get(Setting)} and may keep expensive per-profile objects (compiled
 * patterns ...) with {@link #derived(String, Function)}.
 */
public final class EffectiveSettings {
    private final String profile;
    private final Map<String, Object> values;
    private final Map<String, Map<String, Object>> sections;
    private final Map<String, Object> derived = new ConcurrentHashMap<>();
    private final WordMatcher matcher;
    private final List<String> enabledLanguages;
    private final List<String> activeWords;
    private final Map<String, Integer> tiers = new HashMap<>();
    private final AntiBadWordsSettings legacy;

    EffectiveSettings(String profile, Map<String, Object> values, Map<String, Map<String, Object>> sections, WordLists lists,
                      Consumer<String> warn) {
        this.profile = profile;
        this.values = values;
        this.sections = sections;

        // ---- languages and words (same rules as the old AntiBadWordsPlugin.loadPluginConfig) ----
        int level = get(CoreSettings.PROTECTION_LEVEL);
        Set<String> enabled = get(CoreSettings.ENABLED_LANGUAGES).stream()
            .map(WordLists::canonicalCode).filter(s -> !s.isBlank()).collect(Collectors.toCollection(LinkedHashSet::new));
        enabled.remove(WordLists.CUSTOM);
        enabled.add(WordLists.CUSTOM); // the admin's own words are always on, and come last
        Set<String> active = new LinkedHashSet<>();
        for (String code : enabled) {
            WordList list = lists.get(code);
            if (list == null) {
                if (!code.equals(WordLists.CUSTOM)) {
                    warn.accept(CoreSettings.ENABLED_LANGUAGES.key() + " contains '" + code + "' but there is no file wordlists/" + code + ".yml.");
                }
                continue;
            }
            for (int tier = 4 - level; tier <= 3; tier++) {
                for (String word : list.tiers().get(tier - 1)) {
                    active.add(word);
                    tiers.merge(word, tier, Math::max);
                }
            }
        }
        this.enabledLanguages = List.copyOf(enabled);
        this.activeWords = List.copyOf(active);
        this.matcher = new WordMatcher(active, new WordMatcher.Options(
            get(CoreSettings.COLLAPSE_REPEATS), get(CoreSettings.REPEAT_THRESHOLD), get(CoreSettings.DETECT_SPACED),
            get(CoreSettings.JOIN_ADJACENT), get(CoreSettings.PARTIAL_MATCH), get(CoreSettings.PARTIAL_MIN_LENGTH),
            get(CoreSettings.WHITELIST)));

        List<PunishmentAction> actions = PunishmentParser.parse(section(CoreSettings.EFFECTS), section(CoreSettings.ATTRIBUTES),
            section(CoreSettings.SOUNDS), section(CoreSettings.COMMANDS), warn);
        this.legacy = new AntiBadWordsSettings(
            get(CoreSettings.PUNISHMENT_ENABLED), get(CoreSettings.VIOLATIONS_BEFORE_PUNISH), get(CoreSettings.VIOLATIONS_EXPIRE_MINUTES),
            get(CoreSettings.CLEAR_ON_QUIT), List.copyOf(actions),
            get(CoreSettings.FINAL_COMMAND_ENABLED), get(CoreSettings.FINAL_COMMAND),
            get(CoreSettings.BROADCAST_WARNINGS),
            Names.colour(get(CoreSettings.WARNING_BROADCAST)), Names.colour(get(CoreSettings.WARNING_PLAYER)),
            Names.colour(get(CoreSettings.PUNISHMENT_BROADCAST)), Names.colour(get(CoreSettings.PUNISHMENT_PLAYER)),
            Names.colour(get(CoreSettings.PUNISHMENT_DISABLED_PLAYER)),
            get(CoreSettings.NOTIFY_STAFF), Names.colour(get(CoreSettings.STAFF_NOTIFY_FORMAT)), get(CoreSettings.LOG_CONSOLE),
            get(CoreSettings.SCAN_CHAT), get(CoreSettings.SCAN_COMMANDS), get(CoreSettings.SCAN_SIGNS), get(CoreSettings.SCAN_BOOKS),
            get(CoreSettings.SCAN_ANVIL), lowerCaseSet(get(CoreSettings.SCANNED_COMMANDS)), lowerCaseSet(get(CoreSettings.IGNORED_COMMANDS)));
    }

    private static Set<String> lowerCaseSet(List<String> values) {
        return values.stream().map(s -> s.toLowerCase(Locale.ROOT).trim()).filter(s -> !s.isEmpty()).collect(Collectors.toUnmodifiableSet());
    }

    /** Name of the worlds.yml profile these settings belong to ({@code default} when there are no profiles). */
    public String profile() {
        return profile;
    }

    /** The value of a setting for this profile. */
    @SuppressWarnings("unchecked")
    public <T> T get(Setting<T> setting) {
        Object value = values.get(setting.key());
        return value == null ? setting.defaultValue() : (T) value;
    }

    /** The value by key ({@code null} for unknown keys). Prefer {@link #get(Setting)}. */
    public Object get(String key) {
        return values.get(key);
    }

    /** Raw content of a free-form section for this profile. */
    public Map<String, Object> section(Section section) {
        return sections.getOrDefault(section.key(), Map.of());
    }

    /**
     * Caches an object computed from these settings (for example compiled patterns) for as long as the settings live.
     *
     * @param key a unique name, by convention {@code <check id>.<what>}
     */
    @SuppressWarnings("unchecked")
    public <T> T derived(String key, Function<EffectiveSettings, T> factory) {
        return (T) derived.computeIfAbsent(key, k -> factory.apply(this));
    }

    /** The bad-word matcher for this profile (its languages, protection level, matching options and whitelist). */
    public WordMatcher matcher() {
        return matcher;
    }

    /** Language codes that are on for this profile; {@code custom} is always the last one. */
    public List<String> enabledLanguages() {
        return enabledLanguages;
    }

    /** Every word that is blocked for this profile. */
    public List<String> activeWords() {
        return new ArrayList<>(activeWords);
    }

    /** Tier (1 mild .. 3 severe) of a word returned by the matcher; 3 when unknown. */
    public int tierOf(String word) {
        return tiers.getOrDefault(word, 3);
    }

    /** The classic settings record (messages already coloured) that the punishment flow uses. */
    public AntiBadWordsSettings legacy() {
        return legacy;
    }
}
