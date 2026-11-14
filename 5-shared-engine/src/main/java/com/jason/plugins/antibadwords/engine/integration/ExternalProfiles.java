package com.jason.plugins.antibadwords.engine.integration;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/** Picks a rule profile from the permission plugin: first its meta value, then the group map. Pure logic, no state. */
public final class ExternalProfiles {
    private ExternalProfiles() {
    }

    /**
     * @param groupMap     group name -> profile name, in file order
     * @param profileNames the profiles that exist (lower case)
     * @return the profile name, or {@code null} when the permission plugin has no opinion
     */
    public static String choose(PermissionSource source, UUID player, boolean useMeta, String metaKey, boolean primaryOnly,
                                Map<String, Object> groupMap, Set<String> profileNames) {
        if (source == null || player == null) {
            return null;
        }
        if (useMeta && metaKey != null && !metaKey.isBlank()) {
            Optional<String> value = source.meta(player, metaKey);
            if (value.isPresent()) {
                String name = value.get().trim().toLowerCase(Locale.ROOT);
                if (profileNames.contains(name)) {
                    return name;
                }
            }
        }
        if (groupMap.isEmpty()) {
            return null;
        }
        String primary = source.primaryGroup(player).orElse("");
        for (Map.Entry<String, Object> e : groupMap.entrySet()) {
            String group = e.getKey().trim();
            String profile = String.valueOf(e.getValue()).trim().toLowerCase(Locale.ROOT);
            if (group.isEmpty() || !profileNames.contains(profile)) {
                continue;
            }
            if (group.equalsIgnoreCase(primary) || (!primaryOnly && source.inGroup(player, group))) {
                return profile;
            }
        }
        return null;
    }

    /** Reports map entries that point to a profile that does not exist. */
    public static void validate(Map<String, Object> groupMap, Set<String> profileNames, Consumer<String> warn) {
        for (Map.Entry<String, Object> e : groupMap.entrySet()) {
            String profile = String.valueOf(e.getValue()).trim().toLowerCase(Locale.ROOT);
            if (!profileNames.contains(profile)) {
                warn.accept(IntegrationSettings.GROUP_PROFILES.key() + "." + e.getKey() + ": profile '" + e.getValue()
                    + "' does not exist in worlds.yml; this line is ignored.");
            }
        }
    }
}
