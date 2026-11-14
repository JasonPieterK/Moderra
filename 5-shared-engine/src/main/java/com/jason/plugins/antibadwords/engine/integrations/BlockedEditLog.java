package com.jason.plugins.antibadwords.engine.integrations;

import com.jason.plugins.antibadwords.engine.Engine;
import com.jason.plugins.antibadwords.engine.ModerationEvent;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Forwards blocked / censored sign, book (and optionally anvil) edits to an external log (CoreProtect). The plugin supplies the
 * {@link Sink}; the decision what is logged is made here.
 */
public final class BlockedEditLog {
    /** The external log. Called on the thread that published the event (normally the main thread). */
    public interface Sink {
        /** @return whether the record was written */
        boolean record(UUID player, String playerName, String line);
    }

    private static final int MAX_TEXT = 200;

    private BlockedEditLog() {
    }

    /** Subscribes to the engine's events; call the returned runnable to stop. */
    public static Runnable install(Engine engine, Sink sink) {
        return engine.events().subscribe(event -> handle(engine, sink, event));
    }

    static void handle(Engine engine, Sink sink, ModerationEvent event) {
        if (!ModerationEvent.VIOLATION.equals(event.kind()) || event.playerId() == null || !engine.value(ExtraIntegrationSettings.COREPROTECT)) {
            return;
        }
        String action = event.data().getOrDefault("action", "");
        if (!action.equals("block") && !action.equals("censor")) {
            return;
        }
        Set<String> sources = new HashSet<>();
        for (String s : engine.value(ExtraIntegrationSettings.COREPROTECT_SOURCES)) {
            sources.add(s.toLowerCase(Locale.ROOT));
        }
        if (!sources.contains(event.source().toLowerCase(Locale.ROOT))) {
            return;
        }
        sink.record(event.playerId(), event.playerName(), line(event, engine.value(ExtraIntegrationSettings.COREPROTECT_TEXT)));
    }

    static String line(ModerationEvent event, boolean includeText) {
        StringBuilder sb = new StringBuilder("[AntiBadWords] ").append(event.data().getOrDefault("action", "blocked")).append(' ')
            .append(event.source()).append(" edit");
        if (includeText) {
            String text = event.data().getOrDefault("text", "").replace('\n', ' ');
            if (text.length() > MAX_TEXT) {
                text = text.substring(0, MAX_TEXT) + "...";
            }
            sb.append(": ").append(text);
        }
        return sb.toString();
    }
}
