package com.jason.plugins.moderra.engine;

import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * The ONE place that knows about the legacy permission names. The project was called AntiBadWords before it was renamed to
 * Moderra, and its permissions were {@code antibadwords.*}. For one major version a player who has an old node
 * (for example {@code antibadwords.bypass}) still counts as having the new node ({@code moderra.bypass}). This is
 * the legacy node fallback; it is removed in 2.0.
 *
 * <p>A legacy node counts only when a permission plugin explicitly grants it. Operator defaults and the
 * built-in defaults are never applied to a legacy node (otherwise every operator would bypass the filter).
 * Every permission check of the plugin and the mods goes through {@link #has} or {@link #hasAsync}.
 */
public final class PermissionNodes {
    /** The namespace of all permission nodes. */
    public static final String NAMESPACE = "moderra";
    /** Legacy node namespace (the old project name), accepted as a fallback until 2.0. */
    public static final String LEGACY_NAMESPACE = "antibadwords";

    private PermissionNodes() {
    }

    /** The legacy name of a node ({@code moderra.x} gives {@code antibadwords.x}), or {@code null} for any other node. */
    public static String legacyOf(String node) {
        if (node != null && node.startsWith(NAMESPACE + ".")) {
            return LEGACY_NAMESPACE + node.substring(NAMESPACE.length());
        }
        return null;
    }

    /**
     * @param current   the normal check (with defaults), used for the new node
     * @param explicit  answers only {@code true} when a permission plugin explicitly granted the node (no defaults)
     */
    public static boolean has(Predicate<String> current, Predicate<String> explicit, String node) {
        if (current.test(node)) {
            return true;
        }
        String legacy = legacyOf(node);
        return legacy != null && explicit.test(legacy);
    }

    /** Same for a check that answers later (offline players, LuckPerms): such a check is always explicit. */
    public static CompletableFuture<Boolean> hasAsync(Function<String, CompletableFuture<Boolean>> ask, String node) {
        CompletableFuture<Boolean> current = ask.apply(node);
        String legacy = legacyOf(node);
        if (legacy == null) {
            return current;
        }
        return current.thenCompose(has -> has ? CompletableFuture.completedFuture(true) : ask.apply(legacy));
    }
}
