package com.jason.plugins.antibadwords.engine;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * The engine's internal event stream. Subscribers are called one after the other on the thread that published the
 * event (normally the server's main thread), so slow work (files, network) must be handed to another thread.
 * A subscriber that throws is logged and does not stop the others.
 */
public final class EventBus {
    private final List<Consumer<ModerationEvent>> subscribers = new CopyOnWriteArrayList<>();
    private final EngineLog log;

    EventBus(EngineLog log) {
        this.log = log;
    }

    /** @return call {@code run()} on it to unsubscribe */
    public Runnable subscribe(Consumer<ModerationEvent> subscriber) {
        subscribers.add(subscriber);
        return () -> subscribers.remove(subscriber);
    }

    public void publish(ModerationEvent event) {
        for (Consumer<ModerationEvent> subscriber : subscribers) {
            try {
                subscriber.accept(event);
            } catch (RuntimeException ex) {
                log.warn("An event subscriber failed for '" + event.kind() + "': " + ex);
            }
        }
    }
}
