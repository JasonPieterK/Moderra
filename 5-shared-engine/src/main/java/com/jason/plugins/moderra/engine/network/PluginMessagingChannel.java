package com.jason.plugins.moderra.engine.network;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;

/**
 * Transport over the plugin messaging channel {@link NetworkSettings#CHANNEL}. The loader supplies the {@link Transport}
 * (send a payload over the channel) and calls {@link #receive(byte[])} for every payload that arrives. The payload is
 * the UTF-8 JSON line of the record. Nothing is stored: a server that is offline misses the changes of that time.
 */
public final class PluginMessagingChannel implements SyncChannel {
    /** Sends one payload to the proxy for the other servers. */
    @FunctionalInterface
    public interface Transport {
        void send(String channel, byte[] payload);

        /** False while the loader has no carrier (plugin messages need an online player); records are then queued. */
        default boolean available() {
            return true;
        }
    }

    private final String origin;
    private final Transport transport;
    private final ConcurrentLinkedQueue<SyncRecord> inbox = new ConcurrentLinkedQueue<>();
    /** Records to send once a carrier exists (oldest dropped beyond {@link #MAX_QUEUE}). */
    private final java.util.ArrayDeque<SyncRecord> outbox = new java.util.ArrayDeque<>();
    public static final int MAX_QUEUE = 500;

    public PluginMessagingChannel(String origin, Transport transport) {
        this.origin = origin;
        this.transport = transport;
    }

    @Override
    public void send(SyncRecord record) {
        synchronized (outbox) {
            outbox.add(record);
            while (outbox.size() > MAX_QUEUE) {
                outbox.poll();
            }
            flush();
        }
    }

    /** Sends the queued records while a carrier is available. */
    private void flush() {
        synchronized (outbox) {
            while (!outbox.isEmpty() && transport.available()) {
                SyncRecord next = outbox.peek();
                try {
                    transport.send(NetworkSettings.CHANNEL, next.toLine().getBytes(StandardCharsets.UTF_8));
                } catch (RuntimeException ex) {
                    return; // keep it queued, try again at the next poll
                }
                outbox.poll();
            }
        }
    }

    @Override
    public int queued() {
        synchronized (outbox) {
            return outbox.size();
        }
    }

    /** Called by the loader for an incoming payload. */
    public void receive(byte[] payload) {
        SyncRecord record = SyncRecord.parse(new String(payload, StandardCharsets.UTF_8));
        if (record != null && !origin.equals(record.origin())) {
            inbox.add(record);
        }
    }

    @Override
    public void poll(Consumer<SyncRecord> sink, Consumer<SyncRecord> backlogSink) {
        flush();
        SyncRecord r;
        while ((r = inbox.poll()) != null) {
            sink.accept(r);
        }
    }

    @Override
    public void close() {
    }

    @Override
    public String describe() {
        return "plugin messaging channel " + NetworkSettings.CHANNEL;
    }
}
