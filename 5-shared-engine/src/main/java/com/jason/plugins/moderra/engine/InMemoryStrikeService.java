package com.jason.plugins.moderra.engine;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.LongSupplier;

/**
 * The strike counter of the old plugin: kept in memory only; when an expiry time is set, a player's whole count is
 * forgotten once their last violation is older than that.
 */
public final class InMemoryStrikeService implements StrikeService {
    private record Entry(int count, long lastMillis) {
    }

    private final ConcurrentMap<UUID, Entry> strikes = new ConcurrentHashMap<>();
    private final LongSupplier clock;
    private final LongSupplier expireMillis;

    /**
     * @param clock        current time in milliseconds
     * @param expireMillis how long strikes live, read on every call (0 = for ever)
     */
    public InMemoryStrikeService(LongSupplier clock, LongSupplier expireMillis) {
        this.clock = clock;
        this.expireMillis = expireMillis;
    }

    @Override
    public int add(UUID player, int weight) {
        long now = clock.getAsLong();
        long expire = expireMillis.getAsLong();
        return strikes.merge(player, new Entry(weight, now), (old, ignored) -> {
            boolean expired = expire > 0 && now - old.lastMillis() > expire;
            return new Entry(expired ? weight : old.count() + weight, now);
        }).count();
    }

    @Override
    public int get(UUID player) {
        Entry entry = strikes.get(player);
        if (entry == null) {
            return 0;
        }
        long expire = expireMillis.getAsLong();
        return expire > 0 && clock.getAsLong() - entry.lastMillis() > expire ? 0 : entry.count();
    }

    @Override
    public void reset(UUID player) {
        strikes.remove(player);
    }

    @Override
    public void clear() {
        strikes.clear();
    }
}
