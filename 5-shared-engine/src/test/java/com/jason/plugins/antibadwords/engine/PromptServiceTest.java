package com.jason.plugins.antibadwords.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jason.plugins.antibadwords.engine.config.Setting;
import com.jason.plugins.antibadwords.engine.gui.prompt.PromptKind;
import com.jason.plugins.antibadwords.engine.gui.prompt.PromptService;
import com.jason.plugins.antibadwords.engine.gui.prompt.PromptSettings;
import com.jason.plugins.antibadwords.engine.gui.prompt.TextPrompt;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Chat and sign text prompts with a fake clock. */
class PromptServiceTest {
    @TempDir
    Path dir;
    Fixture f;
    PromptService prompts;
    UUID alice;
    final List<Object> accepted = new ArrayList<>();
    final List<String> cancelled = new ArrayList<>();
    final Setting<Integer> number = Setting.integer("x.number", 5).range(1, 10).title("Number").build();

    @BeforeEach
    void setUp() {
        f = new Fixture(dir);
        f.start();
        prompts = f.engine.prompts();
        alice = f.platform.join("Alice");
    }

    private TextPrompt numberPrompt() {
        return TextPrompt.forSetting(number, accepted::add, () -> cancelled.add("c"));
    }

    private Verdict chat(String text) {
        return f.say(alice, Message.CHAT, text);
    }

    @Test
    void chatAnswerIsCapturedAndNotScannedOrBroadcast() {
        assertEquals(PromptKind.CHAT, prompts.start(alice, numberPrompt()));
        assertTrue(f.platform.sentTo(alice).get(0).contains("Number"));
        Verdict v = chat("7");
        assertTrue(v.captured());
        assertTrue(v.blocked());
        assertFalse(v.hasHits());
        assertEquals(List.of(7), accepted);
        assertFalse(prompts.isActive(alice));
        assertTrue(f.platform.broadcasts.isEmpty());
        // a normal message afterwards is judged normally
        assertFalse(chat("hello").captured());
    }

    @Test
    void answerWithBadWordIsNotCountedAsViolation() {
        prompts.start(alice, TextPrompt.of("Name", "text", false, t -> null, accepted::add, null));
        Verdict v = chat("fuck shit");
        assertTrue(v.captured());
        assertFalse(v.hasHits());
        assertEquals(List.of("fuck shit"), accepted);
    }

    @Test
    void invalidAnswerShowsErrorAndKeepsPromptActive() {
        prompts.start(alice, numberPrompt());
        assertTrue(chat("99").captured());
        assertTrue(accepted.isEmpty());
        assertTrue(prompts.isActive(alice));
        List<String> sent = f.platform.sentTo(alice);
        assertTrue(sent.get(sent.size() - 1).contains("x.number"), sent.toString());
        assertTrue(chat("abc").captured());
        assertTrue(chat("3").captured());
        assertEquals(List.of(3), accepted);
    }

    @Test
    void cancelWordAbortsAnyCase() {
        prompts.start(alice, numberPrompt());
        assertTrue(chat("CaNcEl").captured());
        assertEquals(1, cancelled.size());
        assertTrue(accepted.isEmpty());
        assertFalse(prompts.isActive(alice));
        assertFalse(chat("5").captured());
    }

    @Test
    void timeoutDefaultsTo60sAndIsConfigurable() throws Exception {
        assertEquals(60, f.engine.value(PromptSettings.TIMEOUT).toSeconds());
        prompts.start(alice, numberPrompt());
        f.now.addAndGet(59_000);
        assertTrue(prompts.isActive(alice));
        f.now.addAndGet(2_000);
        assertFalse(chat("4").captured(), "too late: the message is a normal chat message");
        assertEquals(1, cancelled.size());
        assertTrue(accepted.isEmpty());

        f.engine.set(PromptSettings.TIMEOUT, java.time.Duration.ofSeconds(10));
        prompts.start(alice, numberPrompt());
        f.now.addAndGet(9_000);
        assertTrue(chat("x").captured()); // an error restarts the countdown
        f.now.addAndGet(9_000);
        assertTrue(prompts.isActive(alice));
        f.now.addAndGet(2_000);
        assertFalse(prompts.isActive(alice));
        assertEquals(2, cancelled.size());
    }

    @Test
    void secretIsNeverEchoed() {
        Setting<String> hook = Setting.secret("x.hook").title("Hook").build();
        List<String> got = new ArrayList<>();
        prompts.start(alice, TextPrompt.forSetting(hook, got::add, null));
        chat("https://secret.example/token123");
        assertEquals(List.of("https://secret.example/token123"), got);
        for (String line : f.platform.sentTo(alice)) {
            assertFalse(line.contains("token123"), line);
        }
        // an invalid secret: the error must not repeat it
        TextPrompt p = TextPrompt.of("Secret", "", true, t -> "Not accepted.", got::add, null);
        prompts.start(alice, p);
        chat("hunter2");
        for (String line : f.platform.sentTo(alice)) {
            assertFalse(line.contains("hunter2"), line);
        }
    }

    @Test
    void signPathJoinsLinesAndValidates() {
        f.platform.signPrompts = true;
        assertEquals(PromptKind.SIGN, prompts.start(alice, numberPrompt()));
        assertEquals(1, f.platform.signOpened.size());
        assertFalse(prompts.onChat(alice, "5"), "chat is not used for a sign prompt");
        assertTrue(prompts.onSignInput(alice, new String[] {"", "12", "", ""}));
        assertTrue(accepted.isEmpty());
        assertEquals(2, f.platform.signOpened.size(), "re-opened after the error");
        Setting<String> text = Setting.string("x.text", "").title("Text").build();
        List<String> got = new ArrayList<>();
        prompts.start(alice, TextPrompt.forSetting(text, got::add, null));
        assertTrue(prompts.onSignInput(alice, new String[] {" hello ", "big", "", "world"}));
        assertEquals(List.of("hello big world"), got);
        assertFalse(prompts.onSignInput(alice, new String[] {"a", "", "", ""}), "no prompt left");
    }

    @Test
    void emptySignCancelsAndModeChatForcesChat() {
        f.platform.signPrompts = true;
        prompts.start(alice, numberPrompt());
        assertTrue(prompts.onSignInput(alice, new String[] {"", " ", null, ""}));
        assertEquals(1, cancelled.size());
        try {
            f.engine.set(PromptSettings.MODE, "chat");
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
        assertEquals(PromptKind.CHAT, prompts.start(alice, numberPrompt()));
    }

    @Test
    void modesSignAnvilAutoUseTheSignPromptWhereSupported() throws java.io.IOException {
        f.platform.signPrompts = true;
        for (String mode : new String[] {"auto", "sign", "anvil"}) {
            f.engine.set(PromptSettings.MODE, mode);
            assertEquals(PromptKind.SIGN, prompts.start(alice, numberPrompt()), mode);
        }
        f.platform.signPrompts = false;
        f.engine.set(PromptSettings.MODE, "anvil");
        assertEquals(PromptKind.CHAT, prompts.start(alice, numberPrompt()));
    }

    @Test
    void signInputIsRemovedFromTheScreenOnTimeoutCancelAndQuitButNotOnAnswer() {
        f.platform.signPrompts = true;
        prompts.start(alice, numberPrompt());
        f.now.addAndGet(61_000);
        prompts.expireDue();
        assertEquals(List.of(alice), f.platform.signClosed, "time limit");
        prompts.start(alice, numberPrompt());
        prompts.cancel(alice);
        assertEquals(2, f.platform.signClosed.size(), "cancel");
        prompts.start(alice, numberPrompt());
        prompts.onQuit(alice);
        assertEquals(3, f.platform.signClosed.size(), "quit");
        prompts.start(alice, numberPrompt());
        assertTrue(prompts.onSignInput(alice, new String[] {"5", "", "", ""}));
        assertEquals(3, f.platform.signClosed.size(), "an answer closes the sign itself");
        f.platform.signPrompts = false;
        prompts.start(alice, numberPrompt());
        prompts.cancel(alice);
        assertEquals(3, f.platform.signClosed.size(), "a chat prompt has nothing on the screen");
    }

    @Test
    void newPromptReplacesOldAndQuitCancels() {
        prompts.start(alice, numberPrompt());
        prompts.start(alice, numberPrompt());
        assertEquals(1, cancelled.size());
        prompts.onQuit(alice);
        assertEquals(2, cancelled.size());
        assertFalse(prompts.isActive(alice));
    }

    @Test
    void promptsArePerPlayer() {
        UUID bob = f.platform.join("Bob");
        prompts.start(alice, numberPrompt());
        assertFalse(f.say(bob, Message.CHAT, "5").captured());
        assertFalse(f.say(alice, Message.COMMAND, "/home").captured());
        assertTrue(prompts.isActive(alice));
    }
}
