package com.jason.plugins.antibadwords.engine.config;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * Feature "different rules per world or per group" ({@code worlds.yml}).
 *
 * <p>A <em>profile</em> is a named set of overrides for registry settings that are marked overridable. Which profile
 * applies to a message is decided in this order:
 * <ol>
 *   <li>the first profile (in file order) whose permission {@code antibadwords.profile.<name>} the player has
 *       (only when {@code use-permission-profiles} is true),</li>
 *   <li>the first entry under {@code worlds:} whose pattern matches the player's world name or dimension name
 *       ({@code *} = any text, {@code ?} = one character, case is ignored),</li>
 *   <li>the {@code default-profile}.</li>
 * </ol>
 */
public final class WorldProfiles {
    public static final String FILE = "worlds.yml";
    public static final String DEFAULT = "default";
    public static final String PERMISSION_PREFIX = "antibadwords.profile.";
    private static final String CATEGORY = "Worlds";

    public static final Setting<String> DEFAULT_PROFILE = Setting.string("default-profile", DEFAULT).file(FILE).category(CATEGORY)
        .title("Default profile")
        .description("The profile used when no world rule and no permission picks another one.").build();

    public static final Setting<Boolean> USE_PERMISSIONS = Setting.bool("use-permission-profiles", true).file(FILE).category(CATEGORY)
        .title("Profiles by permission")
        .description("When true, a player with the permission antibadwords.profile.<name> always gets that profile,\n"
            + "whatever world they are in. Nobody has these permissions unless you give them.").build();

    public static final Section PROFILES = new Section("profiles", FILE, CATEGORY, "Profiles",
        "Named sets of rules. A profile lists only what is DIFFERENT from the normal files; everything else stays the same.\n"
            + "You can override every setting that CONFIGURATION.md marks with \"per world: yes\", written with its full key.\n"
            + "Example:\n"
            + "  profiles:\n"
            + "    default: {}\n"
            + "    relaxed:\n"
            + "      filters.protection-level: 1\n"
            + "      punishment.enabled: false\n"
            + "    german-lobby:\n"
            + "      filters.enabled-word-lists: [de, en]\n"
            + "It can also be edited in the in-game menu (/abw gui profiles).",
        "default: {}\n", false);

    public static final Section WORLDS = new Section("worlds", FILE, CATEGORY, "World rules",
        "Which world uses which profile: world name (or pattern) on the left, profile name on the right. The first match wins.\n"
            + "* means \"any text\". On Bukkit/Spigot/Paper/Purpur use the world folder name (world, world_nether ...) or the\n"
            + "dimension name (minecraft:overworld, minecraft:the_nether, minecraft:the_end).\n"
            + "Example:\n"
            + "  worlds:\n"
            + "    \"creative_*\": relaxed\n"
            + "    \"minecraft:the_nether\": relaxed\n"
            + "It can also be edited in the in-game menu (/abw gui rules).",
        "{}", false);

    public static void register(SettingsRegistry r) {
        r.fileHeader(FILE, "AntiBadWords - different rules per world or per group of players\n"
            + "Reload with /abw reload. If you do not need this, leave the file as it is.");
        r.register(DEFAULT_PROFILE);
        r.register(USE_PERMISSIONS);
        r.register(PROFILES);
        r.register(WORLDS);
    }

    private record Rule(Pattern pattern, String profile) {
    }

    /** profile name -> (setting or section key -> checked value). The default profile is always present. */
    private final Map<String, Map<String, Object>> overrides;
    private final List<Rule> rules;
    private final String defaultProfile;
    private final boolean usePermissions;

    private WorldProfiles(Map<String, Map<String, Object>> overrides, List<Rule> rules, String defaultProfile, boolean usePermissions) {
        this.overrides = overrides;
        this.rules = rules;
        this.defaultProfile = defaultProfile;
        this.usePermissions = usePermissions;
    }

    public static WorldProfiles none() {
        Map<String, Map<String, Object>> only = new LinkedHashMap<>();
        only.put(DEFAULT, Map.of());
        return new WorldProfiles(only, List.of(), DEFAULT, false);
    }

    /**
     * Reads and checks the two sections of worlds.yml.
     *
     * @param profilesRaw the raw {@code profiles:} map (profile -> overrides, written flat with dotted keys or nested)
     * @param worldsRaw   the raw {@code worlds:} map (pattern -> profile)
     */
    public static WorldProfiles parse(Map<String, Object> profilesRaw, Map<String, Object> worldsRaw, String defaultProfile,
                                      boolean usePermissions, SettingsRegistry registry, List<ConfigProblem> problems) {
        Map<String, Map<String, Object>> overrides = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : profilesRaw.entrySet()) {
            String name = e.getKey().trim().toLowerCase(Locale.ROOT);
            Map<String, Object> checked = new LinkedHashMap<>();
            if (e.getValue() instanceof Map<?, ?> map) {
                Map<String, Object> flat = new LinkedHashMap<>();
                flatten("", YamlTree.stringKeys(map), registry, flat);
                for (Map.Entry<String, Object> o : flat.entrySet()) {
                    String key = o.getKey();
                    String where = "profiles." + name + "." + key;
                    ConfigEntry entry = registry.entry(key);
                    if (entry == null) {
                        problems.add(new ConfigProblem(FILE, where, "unknown setting; it is ignored."));
                    } else if (!entry.overridable()) {
                        problems.add(new ConfigProblem(FILE, where, "this setting cannot be different per world; it is ignored."));
                    } else if (entry instanceof Setting<?> setting) {
                        Setting.Parsed<?> parsed = setting.parse(o.getValue());
                        if (parsed.problem() != null) {
                            problems.add(new ConfigProblem(FILE, where, parsed.problem()));
                        }
                        if (o.getValue() != null) {
                            checked.put(key, parsed.value());
                        }
                    } else if (o.getValue() instanceof Map<?, ?> section) {
                        checked.put(key, YamlTree.stringKeys(section));
                    } else {
                        problems.add(new ConfigProblem(FILE, where, "must be a section (key: value lines); it is ignored."));
                    }
                }
            } else if (e.getValue() != null) {
                problems.add(new ConfigProblem(FILE, "profiles." + name, "must be a section (key: value lines); the profile has no overrides."));
            }
            overrides.put(name, checked);
        }
        overrides.putIfAbsent(DEFAULT, Map.of());

        String def = defaultProfile == null ? DEFAULT : defaultProfile.trim().toLowerCase(Locale.ROOT);
        if (!overrides.containsKey(def)) {
            problems.add(new ConfigProblem(FILE, DEFAULT_PROFILE.key(), "profile '" + def + "' does not exist; using '" + DEFAULT + "'."));
            def = DEFAULT;
        }
        List<Rule> rules = new ArrayList<>();
        for (Map.Entry<String, Object> e : worldsRaw.entrySet()) {
            String profile = String.valueOf(e.getValue()).trim().toLowerCase(Locale.ROOT);
            if (!overrides.containsKey(profile)) {
                problems.add(new ConfigProblem(FILE, "worlds." + e.getKey(), "profile '" + profile + "' does not exist; this rule is ignored."));
                continue;
            }
            rules.add(new Rule(glob(e.getKey().trim()), profile));
        }
        return new WorldProfiles(overrides, rules, def, usePermissions);
    }

    /** Turns nested maps into dotted keys, but stops at registered settings and sections (their value is kept whole). */
    public static void flatten(String prefix, Map<String, Object> map, SettingsRegistry registry, Map<String, Object> out) {
        for (Map.Entry<String, Object> e : map.entrySet()) {
            String key = prefix.isEmpty() ? e.getKey() : prefix + "." + e.getKey();
            if (registry.entry(key) == null && e.getValue() instanceof Map<?, ?> child) {
                flatten(key, YamlTree.stringKeys(child), registry, out);
            } else {
                out.put(key, e.getValue());
            }
        }
    }

    static Pattern glob(String pattern) {
        StringBuilder regex = new StringBuilder();
        for (char c : pattern.toCharArray()) {
            regex.append(c == '*' ? ".*" : c == '?' ? "." : Pattern.quote(String.valueOf(c)));
        }
        return Pattern.compile(regex.toString(), Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    }

    /** Profile names in file order (always contains {@code default}). */
    public Set<String> names() {
        return overrides.keySet();
    }

    /** The checked overrides of one profile (setting/section key -> value); empty for unknown names. */
    public Map<String, Object> overrides(String profile) {
        return overrides.getOrDefault(profile, Map.of());
    }

    public String defaultProfile() {
        return defaultProfile;
    }

    /**
     * Picks the profile for one message.
     *
     * @param world       world name as the platform reports it (may be empty)
     * @param dimension   dimension name such as {@code minecraft:the_nether} (may be empty)
     * @param permissions answers "does the player have this permission?"; {@code null} = unknown player
     */
    public String resolve(String world, String dimension, Predicate<String> permissions) {
        return resolve(world, dimension, permissions, null);
    }

    /**
     * Same, with a profile suggested by a permission plugin (LuckPerms meta or group map, see {@code ExternalProfiles}). It wins
     * over the world rules and the default, but not over an {@code antibadwords.profile.<name>} permission.
     *
     * @param external a profile name, or {@code null}; an unknown name is ignored
     */
    public String resolve(String world, String dimension, Predicate<String> permissions, String external) {
        if (usePermissions && permissions != null) {
            for (String name : overrides.keySet()) {
                if (permissions.test(PERMISSION_PREFIX + name)) {
                    return name;
                }
            }
        }
        if (external != null && overrides.containsKey(external)) {
            return external;
        }
        for (Rule rule : rules) {
            if ((world != null && !world.isEmpty() && rule.pattern().matcher(world).matches())
                || (dimension != null && !dimension.isEmpty() && rule.pattern().matcher(dimension).matches())) {
                return rule.profile();
            }
        }
        return defaultProfile;
    }
}
