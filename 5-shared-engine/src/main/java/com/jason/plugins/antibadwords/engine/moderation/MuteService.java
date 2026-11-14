package com.jason.plugins.antibadwords.engine.moderation;

import com.jason.plugins.antibadwords.Placeholders;
import com.jason.plugins.antibadwords.engine.Engine;
import com.jason.plugins.antibadwords.engine.Message;
import com.jason.plugins.antibadwords.engine.ModerationEvent;
import com.jason.plugins.antibadwords.engine.config.Setting;
import com.jason.plugins.antibadwords.mod.Names;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

/**
 * The built-in mute. Who is muted lives in {@code data/mutes.json} (by UUID, so it also works for players who are
 * offline and survives restarts). {@link Engine#inspect} asks {@link #enforce} first; mutes run out lazily (checked
 * whenever somebody asks) and by a periodic task ({@link MuteSettings#CHECK_EVERY}) that also tells the player and
 * publishes {@code mute-expired}. Events: {@code mute}, {@code unmute}, {@code mute-expired}.
 *
 * <p>Thread-safe. Messages to players and events of expiry found by {@link #enforce} are sent on the server thread
 * ({@code Platform.runSync}), because {@code inspect} may run on any thread.
 */
public final class MuteService implements MuteHandler {
    /** Players with this permission are never held back by a mute. */
    public static final String BYPASS_PERMISSION = "antibadwords.bypass.mute";
    /** Permission for {@code /abw mute}, {@code /abw unmute} and {@code /abw mutes}. */
    public static final String PERMISSION = "antibadwords.mute";
    /** The same notice is not repeated to one player more often than this. */
    private static final long NOTICE_COOLDOWN_MILLIS = 1000;

    /**
     * One mute.
     *
     * @param until when it ends in milliseconds since 1970, or {@link DurationParser#PERMANENT}
     * @param by    the staff member's name, or {@link MuteHandler#SYSTEM}
     */
    public record Mute(UUID id, String name, long since, long until, String reason, String by) {
        public boolean permanent() {
            return DurationParser.isPermanent(until);
        }

        public boolean expired(long now) {
            return !permanent() && now >= until;
        }

        /** Milliseconds left (rounded up to whole seconds), {@link DurationParser#PERMANENT} for a permanent mute. */
        public long remaining(long now) {
            return permanent() ? DurationParser.PERMANENT : Math.max(0, (until - now + 999) / 1000 * 1000);
        }
    }

    private final Engine engine;
    private final Map<UUID, Mute> mutes = new HashMap<>();
    private final Map<UUID, Long> lastNotice = new HashMap<>();
    private final DataFile file;
    private final AtomicBoolean armed = new AtomicBoolean();
    private volatile long lastSweep = Long.MIN_VALUE / 2;

    /** The engine is only used after construction (it may still be being built). */
    public MuteService(Engine engine, Path file, DataFile.Scheduler scheduler) {
        this.engine = engine;
        this.file = new DataFile(file, engine.platform().logger(), engine::now, scheduler, this::snapshot);
    }

    // ------------------------------------------------------------------------------------------
    // State
    // ------------------------------------------------------------------------------------------

    /** Replaces what is in memory with the content of the file (missing or damaged file = nobody muted). */
    public int load() {
        Map<String, Object> root = file.load();
        int skipped = 0;
        synchronized (this) {
            mutes.clear();
            if (root != null && root.get("mutes") instanceof Map<?, ?> saved) {
                for (Map.Entry<?, ?> e : saved.entrySet()) {
                    try {
                        Map<?, ?> row = (Map<?, ?>) e.getValue();
                        UUID id = UUID.fromString(String.valueOf(e.getKey()));
                        String reason = row.get("reason") == null ? "" : String.valueOf(row.get("reason"));
                        String by = row.get("by") == null ? SYSTEM : String.valueOf(row.get("by"));
                        String name = row.get("name") == null ? "" : String.valueOf(row.get("name"));
                        long until = ((Number) row.get("until")).longValue();
                        long since = row.get("since") instanceof Number n ? n.longValue() : 0;
                        mutes.put(id, new Mute(id, name, since, until < 0 ? DurationParser.PERMANENT : until, reason, by));
                    } catch (RuntimeException ex) {
                        skipped++;
                    }
                }
            }
        }
        if (skipped > 0) {
            engine.platform().logger().warn(file.file().getFileName() + ": " + skipped + " entries could not be understood and were skipped.");
        }
        arm();
        return size();
    }

    /** Writes the file now when something changed (server stop). */
    public boolean flush() {
        return file.flush();
    }

    private synchronized Map<String, Object> snapshot() {
        Map<String, Object> rows = new TreeMap<>();
        for (Mute m : mutes.values()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("name", m.name());
            row.put("since", m.since());
            row.put("until", m.until());
            row.put("reason", m.reason());
            row.put("by", m.by());
            rows.put(m.id().toString(), row);
        }
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("version", 1);
        root.put("mutes", rows);
        return root;
    }

    private void changed() {
        file.changed(engine.value(ModerationSettings.SAVE_DELAY).toMillis()); // never while holding the lock
    }

    /** Number of stored mutes (including ones that ran out but were not cleaned up yet). */
    public synchronized int size() {
        return mutes.size();
    }

    // ------------------------------------------------------------------------------------------
    // Asking
    // ------------------------------------------------------------------------------------------

    /** The player's mute, or empty when not muted (or it has run out: that is noticed here). */
    public Optional<Mute> get(UUID player) {
        Mute mute;
        synchronized (this) {
            mute = mutes.get(player);
        }
        if (mute != null && mute.expired(engine.now())) {
            expire(mute);
            return Optional.empty();
        }
        return Optional.ofNullable(mute);
    }

    public boolean isMuted(UUID player) {
        return get(player).isPresent();
    }

    /** Everybody who is muted now, sorted by name. */
    public List<Mute> list() {
        sweepQuietly();
        synchronized (this) {
            return mutes.values().stream().sorted(Comparator.comparing(Mute::name, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(Mute::id)).toList();
        }
    }

    /** A muted player by name (case-insensitive), also when the player is not in the list of known players. */
    public Optional<Mute> findByName(String name) {
        if (name == null || name.isEmpty()) {
            return Optional.empty();
        }
        return list().stream().filter(m -> m.name().equalsIgnoreCase(name)).findFirst();
    }

    // ------------------------------------------------------------------------------------------
    // Muting and unmuting
    // ------------------------------------------------------------------------------------------

    @Override
    public boolean mute(UUID player, String playerName, long durationMillis, String reason, String actor) {
        if (player == null || !engine.value(MuteSettings.ENABLED) || durationMillis == 0) {
            return false;
        }
        long now = engine.now();
        String name = playerName == null || playerName.isEmpty()
            ? engine.players().get(player).map(k -> k.name()).orElse("") : playerName;
        long until = DurationParser.isPermanent(durationMillis) ? DurationParser.PERMANENT : now + durationMillis;
        Mute mute = new Mute(player, name, now, until, reason == null ? "" : reason.strip(), actor == null || actor.isEmpty() ? SYSTEM : actor);
        // EssentialsX mute (integrations.essentials.mute-mode): written there too, or instead of the built-in list
        var essentials = engine.essentials();
        boolean wroteEssentials = essentials.writesEssentialsMute()
            && essentials.setExternalMute(player, name, until, mute.reason());
        if (essentials.writesEssentialsMute() && !wroteEssentials) {
            engine.platform().logger().warn("EssentialsX did not accept the mute of " + name + "; the built-in mute is used for it.");
        }
        if (essentials.keepsOwnMute() || !wroteEssentials) {
            synchronized (this) {
                mutes.put(player, mute);
            }
            changed();
            arm();
        }
        Map<String, String> values = values(mute, now);
        publish(ModerationEvent.MUTE, mute, values);
        tell(player, MuteSettings.MSG_MUTED, values);
        return true;
    }

    /** Lifts a mute. @return false when the player was not muted */
    public boolean unmute(UUID player, String actor) {
        Mute mute = get(player).orElse(null);
        boolean liftedEssentials = engine.essentials().writesEssentialsMute() && engine.essentials().clearExternalMute(player);
        if (mute == null && liftedEssentials) {
            String name = engine.players().get(player).map(k -> k.name()).orElse("");
            mute = new Mute(player, name, engine.now(), engine.now(), "", SYSTEM);
        }
        if (mute == null) {
            return false;
        }
        synchronized (this) {
            mutes.remove(player);
            lastNotice.remove(player);
        }
        changed();
        Map<String, String> values = values(mute, engine.now());
        values.put("by", actor == null || actor.isEmpty() ? SYSTEM : actor);
        publish(ModerationEvent.UNMUTE, mute, values);
        tell(player, MuteSettings.MSG_UNMUTED, values);
        return true;
    }

    /** Removes a mute that has run out, tells the player and publishes {@code mute-expired} (once, even from two threads). */
    private void expire(Mute mute) {
        synchronized (this) {
            if (!mutes.remove(mute.id(), mute)) {
                return; // somebody else was first, or it was replaced
            }
            lastNotice.remove(mute.id());
        }
        changed();
        Map<String, String> values = values(mute, engine.now());
        engine.platform().runSync(() -> {
            publish(ModerationEvent.MUTE_EXPIRED, mute, values);
            tell(mute.id(), MuteSettings.MSG_EXPIRED, values);
        });
    }

    /** Ends every mute that has run out. @return how many */
    public int sweep() {
        long now = engine.now();
        List<Mute> over;
        synchronized (this) {
            over = mutes.values().stream().filter(m -> m.expired(now)).toList();
        }
        over.forEach(this::expire);
        return over.size();
    }

    private void sweepQuietly() {
        try {
            sweep();
        } catch (RuntimeException ex) {
            engine.platform().logger().warn("Checking for finished mutes failed: " + ex);
        }
    }

    /**
     * Makes sure the periodic expiry check is scheduled while timed mutes exist. When the platform runs the task at
     * once (tests) the check does not repeat by itself; call {@link #sweep()} then.
     */
    private void arm() {
        if (!hasTimedMutes() || !armed.compareAndSet(false, true)) {
            return;
        }
        long every = engine.value(MuteSettings.CHECK_EVERY).toMillis();
        engine.platform().runSyncLater(() -> {
            long now = engine.now();
            boolean early = now - lastSweep < every / 2; // ran inline instead of after the delay: do not chain
            lastSweep = now;
            armed.set(false);
            sweepQuietly();
            if (!early) {
                arm();
            }
        }, every);
    }

    private synchronized boolean hasTimedMutes() {
        return mutes.values().stream().anyMatch(m -> !m.permanent());
    }

    // ------------------------------------------------------------------------------------------
    // Enforcement
    // ------------------------------------------------------------------------------------------

    /**
     * Whether this message must be stopped because its author is muted (called by {@link Engine#inspect} before any
     * other check). Tells the player why, at most once a second.
     */
    public boolean enforce(Message message) {
        UUID player = message.playerId();
        if (player == null || message.probe() || !engine.value(MuteSettings.ENABLED) || !covers(message)) {
            return false;
        }
        Mute mute = get(player).orElse(null);
        if (mute == null) {
            // a mute made in EssentialsX (mute-mode essentials or both) holds the player back here too
            mute = engine.essentials().externalMute(player)
                .map(e -> new Mute(player, message.playerName(), 0, e.until() <= 0 ? DurationParser.PERMANENT : e.until(), e.reason() == null ? "" : e.reason(), "essentials"))
                .filter(m -> !m.expired(engine.now())).orElse(null);
        }
        if (mute == null || message.has(BYPASS_PERMISSION)) {
            return false;
        }
        long now = engine.now();
        boolean notice;
        synchronized (this) {
            Long last = lastNotice.get(player);
            notice = last == null || now - last >= NOTICE_COOLDOWN_MILLIS || now < last;
            if (notice) {
                lastNotice.put(player, now);
            }
        }
        if (notice) {
            Map<String, String> values = values(mute, now);
            engine.platform().runSync(() -> tell(player, MuteSettings.MSG_BLOCKED, values));
        }
        return true;
    }

    /** Whether a muted player may not send this kind of message. */
    private boolean covers(Message message) {
        return switch (message.source()) {
            case Message.CHAT -> engine.value(MuteSettings.BLOCK_CHAT);
            case Message.SIGN, Message.SIGN_BACK -> engine.value(MuteSettings.BLOCK_SIGNS);
            case Message.BOOK -> engine.value(MuteSettings.BLOCK_BOOKS);
            case Message.COMMAND -> commandBlocked(message.text());
            default -> false;
        };
    }

    private boolean commandBlocked(String commandLine) {
        if (commandLine == null || commandLine.isBlank()) {
            return false;
        }
        String stripped = commandLine.strip();
        int space = stripped.indexOf(' ');
        String label = Names.commandLabel(space < 0 ? stripped : stripped.substring(0, space));
        for (String pattern : engine.value(MuteSettings.BLOCKED_COMMANDS)) {
            if (matches(pattern, label)) {
                return true;
            }
        }
        return false;
    }

    /** {@code *} in the pattern matches any run of characters. Both sides are compared without case. */
    public static boolean matches(String pattern, String label) {
        String p = Names.commandLabel(pattern.strip());
        if (p.isEmpty()) {
            return false;
        }
        if (p.indexOf('*') < 0) {
            return p.equals(label);
        }
        StringBuilder regex = new StringBuilder();
        for (String part : p.split("\\*", -1)) {
            if (regex.length() > 0) {
                regex.append(".*");
            }
            regex.append(Pattern.quote(part));
        }
        return Pattern.matches(regex.toString(), label);
    }

    /** Join: tells a player who is still muted, and keeps the stored name up to date. */
    public void onJoin(UUID player, String name) {
        Mute mute = get(player).orElse(null);
        if (mute == null) {
            return;
        }
        if (name != null && !name.isEmpty() && !name.equals(mute.name())) {
            Mute renamed = new Mute(mute.id(), name, mute.since(), mute.until(), mute.reason(), mute.by());
            synchronized (this) {
                mutes.replace(player, mute, renamed);
            }
            changed();
            mute = renamed;
        }
        if (engine.value(MuteSettings.ENABLED) && engine.value(MuteSettings.NOTIFY_ON_JOIN)) {
            tell(player, MuteSettings.MSG_JOIN, values(mute, engine.now()));
        }
    }

    // ------------------------------------------------------------------------------------------
    // Texts and events
    // ------------------------------------------------------------------------------------------

    /** Placeholders of the messages: player, reason, remaining, duration, by. */
    public Map<String, String> values(Mute mute, long now) {
        Map<String, String> values = new HashMap<>();
        values.put("player", mute.name());
        values.put("reason", mute.reason().isEmpty() ? engine.value(MuteSettings.NO_REASON) : mute.reason());
        values.put("remaining", DurationParser.format(mute.remaining(now)));
        values.put("duration", DurationParser.format(mute.permanent() ? DurationParser.PERMANENT : Math.max(0, mute.until() - mute.since())));
        values.put("by", mute.by());
        return values;
    }

    private void tell(UUID player, Setting<String> message, Map<String, String> values) {
        String text = engine.value(message);
        if (text != null && !text.isBlank()) {
            engine.platform().sendMessage(player, Names.colour(Placeholders.apply(text, values)));
        }
    }

    private void publish(String kind, Mute mute, Map<String, String> values) {
        Map<String, String> data = new HashMap<>();
        data.put("reason", mute.reason());
        data.put("by", values.get("by"));
        data.put("duration", values.get("duration"));
        data.put("until", Long.toString(mute.until()));
        data.put("permanent", Boolean.toString(mute.permanent()));
        String source = MuteHandler.SYSTEM.equals(mute.by()) ? "ladder" : "command";
        engine.events().publish(new ModerationEvent(kind, engine.now(), mute.id(), mute.name(), engine.platform().worldName(mute.id()), source, data));
    }

    /** All muted players as a plain list, for tests and status output. */
    public synchronized List<Mute> all() {
        return new ArrayList<>(mutes.values());
    }
}
