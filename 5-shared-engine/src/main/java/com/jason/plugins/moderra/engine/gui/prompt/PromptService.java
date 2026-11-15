package com.jason.plugins.moderra.engine.gui.prompt;

import com.jason.plugins.moderra.Placeholders;
import com.jason.plugins.moderra.engine.Engine;
import com.jason.plugins.moderra.engine.Platform;
import com.jason.plugins.moderra.mod.Names;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * The active text prompt of each player (at most one). A menu calls {@link #start}; the loader then forwards what the
 * player types:
 * <ul>
 *   <li>chat: {@code Engine.inspect} calls {@link #onChat} first; when it returns {@code true} the verdict is
 *       {@code Verdict.captured()}: the loader must not broadcast, scan, store or publish events for that message;</li>
 *   <li>sign: the loader calls {@link #onSignInput} with the lines (it cancels the sign edit when this returns true);</li>
 *   <li>{@link #onQuit} when the player leaves.</li>
 * </ul>
 * Timeouts are checked on every call and by a delayed task on the platform ({@link #expireDue()}).
 * Typed text is never logged or echoed (not even in error messages). Callbacks run through {@link Platform#runSync}.
 */
public final class PromptService {
    private static final class Active {
        final TextPrompt prompt;
        final PromptKind kind;
        volatile long deadline;

        Active(TextPrompt prompt, PromptKind kind) {
            this.prompt = prompt;
            this.kind = kind;
        }
    }

    private final Engine engine;
    private final Platform platform;
    private final LongSupplier clock;
    private final Map<UUID, Active> active = new ConcurrentHashMap<>();

    public PromptService(Engine engine, Platform platform, LongSupplier clock) {
        this.engine = engine;
        this.platform = platform;
        this.clock = clock;
    }

    /** SIGN where the setting and the platform allow it, else CHAT. */
    public PromptKind kindFor() {
        boolean sign = !"chat".equals(engine.value(PromptSettings.MODE)) && platform.supportsSignPrompt();
        return sign ? PromptKind.SIGN : PromptKind.CHAT;
    }

    /** Starts a prompt for the player; an earlier prompt of that player is cancelled (its cancel callback runs). */
    public PromptKind start(UUID player, TextPrompt prompt) {
        Active next = new Active(prompt, kindFor());
        next.deadline = clock.getAsLong() + timeoutMillis();
        Active old = active.put(player, next);
        if (old != null) {
            platform.runSync(old.prompt::cancelled);
        }
        Map<String, String> values = Map.of("title", prompt.title(), "hint", prompt.hint(), "cancel", cancelWord(),
            "seconds", String.valueOf(timeoutMillis() / 1000));
        say(player, Placeholders.apply(engine.value(next.kind == PromptKind.SIGN ? PromptSettings.MESSAGE_SIGN
            : PromptSettings.MESSAGE_CHAT), values));
        if (next.kind == PromptKind.SIGN) {
            platform.openSignPrompt(player, prompt.title(), prompt.hint());
        }
        platform.runSyncLater(this::expireDue, timeoutMillis() + 50);
        return next.kind;
    }

    public boolean isActive(UUID player) {
        return activeKind(player) != null;
    }

    /** The kind of the player's active prompt, or {@code null}. */
    public PromptKind activeKind(UUID player) {
        expireDue();
        Active a = active.get(player);
        return a == null ? null : a.kind;
    }

    /**
     * Offers a chat message. Returns {@code true} when it belonged to a chat prompt and was used up (accepted, rejected
     * with an error, or taken as cancel); the loader must then drop the message completely.
     */
    public boolean onChat(UUID player, String text) {
        expireDue();
        Active a = active.get(player);
        if (a == null || a.kind != PromptKind.CHAT) {
            return false;
        }
        String typed = text == null ? "" : text.trim();
        if (typed.equalsIgnoreCase(cancelWord())) {
            abort(player, a, PromptSettings.MESSAGE_CANCELLED);
        } else {
            submit(player, a, typed);
        }
        return true;
    }

    /**
     * Offers the lines of a finished sign edit. Lines are trimmed, empty ones dropped, the rest joined with one space.
     * All lines empty = cancel. Returns {@code true} when the player has an active sign prompt (cancel the sign edit).
     */
    public boolean onSignInput(UUID player, String[] lines) {
        expireDue();
        Active a = active.get(player);
        if (a == null || a.kind != PromptKind.SIGN) {
            return false;
        }
        StringBuilder joined = new StringBuilder();
        if (lines != null) {
            for (String line : lines) {
                String t = line == null ? "" : line.trim();
                if (!t.isEmpty()) {
                    if (joined.length() > 0) {
                        joined.append(' ');
                    }
                    joined.append(t);
                }
            }
        }
        if (joined.length() == 0) {
            abort(player, a, PromptSettings.MESSAGE_CANCELLED);
        } else {
            submit(player, a, joined.toString());
        }
        return true;
    }

    /** Cancels a prompt without a chat message (menu closed, player quit). */
    public boolean cancel(UUID player) {
        Active a = active.get(player);
        if (a == null || !active.remove(player, a)) {
            return false;
        }
        closeScreen(player, a);
        platform.runSync(a.prompt::cancelled);
        return true;
    }

    public void onQuit(UUID player) {
        cancel(player);
    }

    /** Cancels every prompt whose time is up. Called by the scheduled task; also safe to call from a tick. */
    public void expireDue() {
        long now = clock.getAsLong();
        for (Map.Entry<UUID, Active> e : active.entrySet()) {
            if (now >= e.getValue().deadline) {
                abort(e.getKey(), e.getValue(), PromptSettings.MESSAGE_TIMEOUT);
            }
        }
    }

    private void abort(UUID player, Active a, com.jason.plugins.moderra.engine.config.Setting<String> message) {
        if (active.remove(player, a)) {
            say(player, engine.value(message));
            closeScreen(player, a);
            platform.runSync(a.prompt::cancelled);
        }
    }

    /** A sign or anvil input that ended without an answer must not stay on the player's screen. */
    private void closeScreen(UUID player, Active a) {
        if (a.kind == PromptKind.SIGN) {
            platform.runSync(() -> platform.closeSignPrompt(player));
        }
    }

    private void submit(UUID player, Active a, String text) {
        String error = a.prompt.validate(text);
        if (error != null) {
            a.deadline = clock.getAsLong() + timeoutMillis();
            say(player, Placeholders.apply(engine.value(PromptSettings.MESSAGE_ERROR), Map.of("error", error, "cancel", cancelWord())));
            if (a.kind == PromptKind.SIGN) {
                platform.openSignPrompt(player, a.prompt.title(), a.prompt.hint());
            }
            platform.runSyncLater(this::expireDue, timeoutMillis() + 50);
            return;
        }
        if (active.remove(player, a)) {
            platform.runSync(() -> a.prompt.accept(text));
        }
    }

    private void say(UUID player, String text) {
        if (text == null || text.isEmpty()) {
            return;
        }
        for (String line : text.replace("\\n", "\n").split("\n")) {
            platform.sendMessage(player, Names.colour(line));
        }
    }

    private long timeoutMillis() {
        return engine.value(PromptSettings.TIMEOUT).toMillis();
    }

    private String cancelWord() {
        String w = engine.value(PromptSettings.CANCEL_WORD);
        return w == null || w.isBlank() ? "cancel" : w.trim();
    }
}
