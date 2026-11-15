package com.jason.plugins.antibadwords.engine.network;

import com.jason.plugins.antibadwords.engine.Engine;
import com.jason.plugins.antibadwords.engine.moderation.DurationParser;
import com.jason.plugins.antibadwords.engine.moderation.MuteService;
import com.jason.plugins.antibadwords.engine.moderation.PersistentStrikeService;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * One shared mute list and strike count for the servers of a network ({@code integrations.network.mode}).
 *
 * <p>Local mutes, unmutes, strikes and resets are written to the {@link SyncChannel}; changes of other servers are
 * applied through the {@code applyRemote...} methods of the services (which do not pass them on again).
 *
 * <h2>Conflict rules</h2>
 * <ul>
 *   <li>Mutes: per player the change with the newest timestamp wins; equal timestamps: the larger server id wins.
 *       An unmute is a change like a mute. A mute that has already run out is ignored.</li>
 *   <li>Strikes: points are added (adding commutes, so no strike is lost when two servers add at once). A reset is
 *       newest-wins too: strikes older than the newest reset of that player are dropped.</li>
 *   <li>A server read for the first time (its backlog) contributes mutes only; its old strikes are not counted, so a
 *       new server does not inherit stale points.</li>
 *   <li>Clocks: timestamps are wall clock time, so the servers should keep their clocks in sync (NTP).</li>
 * </ul>
 * Thread-safe; polling runs off the server thread when the platform can do that.
 */
public final class NetworkSync {
    private record Stamp(long ts, String origin) {
        boolean newerThan(Stamp other) {
            return other == null || ts > other.ts || (ts == other.ts && origin.compareTo(other.origin) > 0);
        }
    }

    private final Engine engine;
    private final Map<UUID, Stamp> muteStamps = new ConcurrentHashMap<>();
    private final Map<UUID, Stamp> resetStamps = new ConcurrentHashMap<>();
    private volatile SyncChannel channel;
    private volatile String origin = "";
    private volatile String mode = NetworkSettings.OFF;
    private volatile PluginMessagingChannel.Transport transport;
    private volatile boolean syncMutes;
    private volatile boolean syncStrikes;
    private volatile long generation;
    private final Map<String, Long> peers = new ConcurrentHashMap<>();
    private volatile long lastSync;
    private volatile MuteService mutes;
    private volatile PersistentStrikeService strikes;

    public NetworkSync(Engine engine) {
        this.engine = engine;
    }

    /** Hooks the services and starts following the settings (called once by the engine). */
    public void install(MuteService muteService, PersistentStrikeService strikeService) {
        this.mutes = muteService;
        this.strikes = strikeService;
        muteService.setListener(new MuteService.Listener() {
            @Override
            public void muted(MuteService.Mute mute) {
                send(SyncRecord.MUTE, SyncRecord.mute(origin, engine.now(), mute.id(), mute.name(), mute.until(), mute.reason(), mute.by()));
            }

            @Override
            public void unmuted(UUID player) {
                send(SyncRecord.UNMUTE, SyncRecord.unmute(origin, engine.now(), player));
            }
        });
        strikeService.setListener(new PersistentStrikeService.Listener() {
            @Override
            public void added(UUID player, int weight) {
                send(SyncRecord.STRIKE, SyncRecord.strike(origin, engine.now(), player, weight));
            }

            @Override
            public void reset(UUID player) {
                send(SyncRecord.RESET, SyncRecord.reset(origin, engine.now(), player));
            }
        });
        engine.addReloadListener(this::configure);
    }

    /** The loader that can carry plugin messages gives the transport here (before or after the engine loads). */
    public void setTransport(PluginMessagingChannel.Transport transport) {
        this.transport = transport;
    }

    /** Payload of a plugin message received on {@link NetworkSettings#CHANNEL}. */
    public void receive(byte[] payload) {
        if (channel instanceof PluginMessagingChannel pm) {
            pm.receive(payload);
        }
    }

    /** The active mode ({@code off} when the configured one could not start). */
    public String mode() {
        return channel == null ? NetworkSettings.OFF : mode;
    }

    public String serverId() {
        return origin;
    }

    private void send(String kind, SyncRecord record) {
        SyncChannel c = channel;
        if (c == null) {
            return;
        }
        boolean isMute = SyncRecord.MUTE.equals(kind) || SyncRecord.UNMUTE.equals(kind);
        if (isMute ? !syncMutes : !syncStrikes) {
            return;
        }
        Stamp stamp = new Stamp(record.ts(), origin);
        if (isMute) {
            muteStamps.merge(record.player(), stamp, (a, b) -> b.newerThan(a) ? b : a);
        } else if (SyncRecord.RESET.equals(kind)) {
            resetStamps.merge(record.player(), stamp, (a, b) -> b.newerThan(a) ? b : a);
        }
        c.send(record);
        lastSync = engine.now();
    }

    /** Other servers seen so far (server id to the time of their last record). */
    public Map<String, Long> peers() {
        return java.util.Collections.unmodifiableMap(new java.util.TreeMap<>(peers));
    }

    /** Time (engine clock) of the last record sent or received, 0 if none yet. */
    public long lastSync() {
        return lastSync;
    }

    /** Records waiting for a carrier (plugin messaging, nobody online). */
    public int queued() {
        SyncChannel c = channel;
        return c == null ? 0 : c.queued();
    }

    private synchronized void configure() {
        String wanted = engine.value(NetworkSettings.MODE);
        syncMutes = engine.value(NetworkSettings.SYNC_MUTES);
        syncStrikes = engine.value(NetworkSettings.SYNC_STRIKES);
        String id = resolveServerId();
        SyncChannel old = channel;
        if (old != null && wanted.equals(mode) && id.equals(origin) && sameFolder(old)) {
            return; // nothing relevant changed
        }
        generation++;
        if (old != null) {
            old.close();
            channel = null;
        }
        mode = wanted;
        origin = id;
        var log = engine.platform().logger();
        if (NetworkSettings.OFF.equals(wanted)) {
            if (old != null) {
                log.info("Network sync: off.");
            }
            return;
        }
        SyncChannel created = null;
        if (NetworkSettings.SHARED_FOLDER.equals(wanted)) {
            created = new SharedFolderChannel(folder(), id, log);
        } else if (transport == null) {
            log.warn("integrations.network.mode is plugin-messaging but this loader provides no plugin messaging transport: "
                + "nothing is synced. Use shared-folder instead.");
            return;
        } else {
            created = new PluginMessagingChannel(id, transport);
        }
        channel = created;
        log.info("Network sync: " + created.describe() + " (mutes " + (syncMutes ? "on" : "off") + ", strikes " + (syncStrikes ? "on" : "off") + ").");
        schedule(generation);
    }

    private boolean sameFolder(SyncChannel c) {
        return !(c instanceof SharedFolderChannel) || c.describe().startsWith("shared folder " + folder().toAbsolutePath().normalize() + " ");
    }

    private Path folder() {
        String text = engine.value(NetworkSettings.FOLDER).strip();
        Path path = Path.of(text.isEmpty() ? "../abw-network" : text);
        return path.isAbsolute() ? path : engine.platform().dataFolder().resolve(path).normalize();
    }

    private String resolveServerId() {
        String configured = engine.value(NetworkSettings.SERVER_ID).strip().replaceAll("[^A-Za-z0-9_-]", "");
        if (!configured.isEmpty()) {
            return configured;
        }
        Path here = engine.platform().dataFolder().toAbsolutePath().normalize();
        return "server-" + Integer.toHexString(here.toString().hashCode());
    }

    private void schedule(long gen) {
        long delay = Math.max(250, engine.value(NetworkSettings.POLL).toMillis());
        long scheduledAt = System.nanoTime();
        engine.platform().runSyncLater(() -> engine.platform().runAsync(() -> {
            if (gen != generation || channel == null) {
                return;
            }
            poll();
            // a platform that runs "later" tasks at once (tests) must not make this loop spin
            if (System.nanoTime() - scheduledAt >= delay * 500_000L) {
                schedule(gen);
            }
        }), delay);
    }

    /** Reads what other servers changed and applies it. Called by the poll task; public for tests and /abw commands. */
    public void poll() {
        SyncChannel c = channel;
        if (c == null) {
            return;
        }
        try {
            c.poll(r -> apply(r, true), r -> apply(r, false));
        } catch (RuntimeException ex) {
            engine.platform().logger().warn("Network sync failed: " + ex);
        }
    }

    private void apply(SyncRecord r, boolean live) {
        if (r.origin().equals(origin)) {
            return;
        }
        peers.put(r.origin(), engine.now());
        lastSync = engine.now();
        Stamp stamp = new Stamp(r.ts(), r.origin());
        switch (r.kind()) {
            case SyncRecord.MUTE -> {
                if (!syncMutes || !stamp.newerThan(muteStamps.get(r.player()))) {
                    return;
                }
                muteStamps.put(r.player(), stamp);
                long until = r.until() < 0 ? DurationParser.PERMANENT : r.until();
                mutes.applyRemoteMute(new MuteService.Mute(r.player(), r.name(), r.ts(), until, r.reason(), r.by()));
            }
            case SyncRecord.UNMUTE -> {
                if (!syncMutes || !stamp.newerThan(muteStamps.get(r.player()))) {
                    return;
                }
                muteStamps.put(r.player(), stamp);
                mutes.applyRemoteUnmute(r.player());
            }
            case SyncRecord.STRIKE -> {
                Stamp reset = resetStamps.get(r.player());
                if (!syncStrikes || !live || r.weight() <= 0 || (reset != null && !stamp.newerThan(reset))) {
                    return;
                }
                strikes.addRemote(r.player(), r.weight());
            }
            case SyncRecord.RESET -> {
                if (!syncStrikes || !stamp.newerThan(resetStamps.get(r.player()))) {
                    return;
                }
                resetStamps.put(r.player(), stamp);
                if (live) {
                    strikes.resetRemote(r.player());
                }
            }
            default -> {
            }
        }
    }

    public void shutdown() {
        generation++;
        SyncChannel c = channel;
        channel = null;
        if (c != null) {
            c.close();
        }
    }
}
