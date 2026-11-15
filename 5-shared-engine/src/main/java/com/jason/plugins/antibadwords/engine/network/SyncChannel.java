package com.jason.plugins.antibadwords.engine.network;

import java.util.function.Consumer;

/** How changes travel between the servers of a network. Implementations must be thread-safe. */
public interface SyncChannel {
    /** Sends a local change to the other servers. Never throws. */
    void send(SyncRecord record);

    /**
     * Hands every change of other servers that arrived since the last call to a sink.
     *
     * @param sink        records that arrived live
     * @param backlogSink records of a source that was never read before (the history of a server seen for the first time)
     */
    void poll(Consumer<SyncRecord> sink, Consumer<SyncRecord> backlogSink);

    /** Writes pending state (read positions). */
    void close();

    /** Short description for the log. */
    String describe();
}
