package com.jason.plugins.antibadwords.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jason.plugins.antibadwords.engine.config.CoreSettings;
import com.jason.plugins.antibadwords.engine.config.Setting;
import com.jason.plugins.antibadwords.engine.filters.Similarity;
import com.jason.plugins.antibadwords.engine.filters.SpamSettings;
import com.jason.plugins.antibadwords.engine.filters.SpamState;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Spam and flood control with a clock the test moves by hand. */
class SpamFilterTest {
    @TempDir
    Path dir;
    Fixture f;
    Engine engine;
    UUID bob;
    final List<ModerationEvent> events = new ArrayList<>();

    @BeforeEach
    void setUp() {
        f = new Fixture(dir);
        engine = f.start();
        bob = f.platform.join("Bob");
        f.platform.rewritable.add(Message.CHAT); // like a Bukkit server
        engine.events().subscribe(events::add);
    }

    private <T> void set(Setting<T> setting, T value) {
        try {
            engine.set(setting, value);
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
        assertTrue(engine.problems().isEmpty(), engine.problems().toString());
    }

    private Verdict chat(String text) {
        return f.say(bob, Message.CHAT, text);
    }

    private void seconds(double s) {
        f.now.addAndGet((long) (s * 1000));
    }

    /** Only the named rule is on, so the tests do not disturb each other. */
    private void only(SpamSettings.Rule rule) {
        set(SpamSettings.ENABLED, true);
        for (SpamSettings.Rule r : List.of(SpamSettings.RATE, SpamSettings.REPEAT, SpamSettings.CAPS, SpamSettings.FLOOD, SpamSettings.WORDS,
            SpamSettings.COMMANDS)) {
            set(r.enabled(), r == rule);
        }
    }

    @Test
    void everythingIsOffByDefault() {
        assertFalse(engine.settings().get(SpamSettings.ENABLED));
        for (int i = 0; i < 30; i++) {
            assertFalse(chat("SAME MESSAGE AGAIN AND AGAIN !!!!!!!!!!!!").hasHits());
            assertFalse(f.say(bob, Message.COMMAND, "/home").hasHits());
        }
        assertEquals(0, engine.spamState().size(), "nothing is remembered while the filter is off");
        assertTrue(events.isEmpty());
        assertTrue(TestFiles.read(f.file("filters/spam.yml")).contains("enabled: false"));
    }

    @Test
    void rateLimitCountsMessagesInASlidingWindow() {
        only(SpamSettings.RATE);
        set(SpamSettings.RATE_MAX, 3);
        set(SpamSettings.RATE_WINDOW, Duration.ofSeconds(10));
        for (int i = 0; i < 3; i++) {
            assertTrue(chat("message number " + i).allowed());
            seconds(1);
        }
        Verdict fourth = chat("one too many");
        assertTrue(fourth.blocked());
        assertEquals("spam-rate", fourth.primary().check());
        assertEquals("too many messages", fourth.primary().detail());
        assertEquals(List.of("§cYou are sending messages too fast. Please slow down."), f.platform.sentTo(bob));
        assertEquals(0, engine.strikes().get(bob), "strike weight 0 by default: spam never punishes");
        assertTrue(f.platform.broadcasts.isEmpty());
        assertEquals(List.of("spam", "violation"), events.stream().map(ModerationEvent::kind).toList());
        assertEquals("spam-rate", events.get(0).data().get("check"));
        assertEquals("block", events.get(0).data().get("action"));
        assertEquals("one too many", events.get(0).data().get("text"));
        assertTrue(f.platform.infos.contains("Bob triggered spam-rate (too many messages) in chat: blocked"));

        assertTrue(chat("still too fast").blocked(), "blocked attempts count too, so hammering does not help");
        seconds(11);
        assertTrue(chat("after a pause").allowed(), "the window slid past the old messages");

        // other players are counted separately; signs are not in rate.sources
        UUID amy = f.platform.join("Amy");
        seconds(11);
        for (int i = 0; i < 3; i++) {
            assertTrue(chat("b" + i).allowed());
            assertTrue(f.say(amy, Message.CHAT, "a" + i).allowed());
            assertTrue(f.say(bob, Message.SIGN, "sign text").allowed());
        }
        assertTrue(chat("b3").blocked());
        assertTrue(f.say(bob, Message.SIGN, "sign text").allowed());
        set(SpamSettings.RATE.sources(), List.of("chat", "sign"));
        assertTrue(f.say(bob, Message.SIGN, "sign text").blocked(), "now signs count as well");
    }

    @Test
    void rateActionWeightMessageAndBypassAreConfigurable() {
        only(SpamSettings.RATE);
        set(SpamSettings.RATE_MAX, 1);
        set(SpamSettings.RATE.action(), "log-only");
        chat("one");
        Verdict second = chat("two");
        assertTrue(second.allowed() && second.hasHits(), "log-only lets the message through");
        assertTrue(f.platform.sentTo(bob).isEmpty());

        set(SpamSettings.RATE.action(), "block");
        set(SpamSettings.RATE.message(), "&6Slow down, {player}!");
        set(SpamSettings.RATE.weight(), 1);
        assertTrue(chat("three").blocked());
        assertEquals("§6Slow down, Bob!", f.platform.sentTo(bob).get(0));
        assertEquals(1, engine.strikes().get(bob), "with a strike weight the normal strike system takes over");
        set(SpamSettings.RATE.message(), "");
        set(SpamSettings.RATE.weight(), 0);
        f.platform.sentTo(bob).clear();
        assertTrue(chat("four").blocked());
        assertTrue(f.platform.sentTo(bob).isEmpty(), "empty message = silent");

        UUID vip = f.platform.join("Vip", SpamSettings.PERMISSION_BYPASS);
        for (int i = 0; i < 5; i++) {
            assertFalse(f.say(vip, Message.CHAT, "vip " + i).hasHits());
        }
        // the general bypass covers spam as well
        UUID admin = f.platform.join("Admin", Engine.PERMISSION_BYPASS);
        for (int i = 0; i < 5; i++) {
            assertFalse(f.say(admin, Message.CHAT, "admin " + i).hasHits());
        }
    }

    @Test
    void repeatedAndNearlyIdenticalMessages() {
        only(SpamSettings.REPEAT);
        assertTrue(chat("buy cheap diamonds at my shop").allowed());
        seconds(2);
        assertTrue(chat("Buy cheap   DIAMONDS at my shop").allowed(), "second time is still allowed (max-repeats 2)");
        seconds(2);
        Verdict third = chat("buy cheap diamonds at my shop!!");
        assertTrue(third.blocked(), "nearly the same text a third time");
        assertEquals("spam-repeat", third.primary().check());
        assertEquals("§cPlease do not repeat the same message.", f.platform.sentTo(bob).get(0));
        assertTrue(chat("something completely different").allowed());
        assertTrue(chat("ok").allowed() && chat("ok").allowed() && chat("ok").allowed() && chat("ok").allowed(), "short messages are not counted");

        seconds(31);
        assertTrue(chat("buy cheap diamonds at my shop").allowed(), "forgotten after the window");

        // only identical text
        set(SpamSettings.REPEAT_SIMILARITY, 1.0);
        set(SpamSettings.REPEAT_MAX, 1);
        seconds(31);
        assertTrue(chat("hello there friend").allowed());
        assertTrue(chat("hello there friend!").allowed(), "one character differs");
        assertTrue(chat("HELLO  there friend").blocked(), "case and spaces never matter");

        // history-size bounds what is remembered
        set(SpamSettings.REPEAT_HISTORY, 2);
        seconds(31);
        chat("first message here");
        chat("second message here");
        chat("third message here");
        assertTrue(chat("first message here").allowed(), "pushed out of the 2-message history");
    }

    @Test
    void capitalLetters() {
        only(SpamSettings.CAPS);
        Verdict shout = chat("STOP SHOUTING AT ME please");
        assertTrue(shout.censored(), "default action: censor");
        assertEquals("stop shouting at me please", shout.text());
        assertEquals("spam-caps", shout.primary().check());
        assertTrue(f.platform.sentTo(bob).isEmpty());
        assertTrue(chat("LOL GG").allowed(), "shorter than min-length");
        assertTrue(chat("Hello Everyone, Nice To Meet You").allowed(), "normal capitalisation");
        assertTrue(chat("ПРИВЕТ ВСЕМ КАК ДЕЛА").censored(), "works for every alphabet with capitals");
        assertTrue(chat("12345 67890 !!! ???").allowed(), "no letters");
        assertTrue(chat("你好你好你好你好你好").allowed(), "no capitals in this script");

        Verdict sign = f.say(bob, Message.SIGN, "STOP SHOUTING AT ME");
        assertTrue(sign.allowed(), "caps.sources is chat only by default");
        set(SpamSettings.CAPS.sources(), List.of("*"));
        sign = f.say(bob, Message.SIGN, "STOP SHOUTING AT ME");
        assertTrue(sign.blocked(), "a sign cannot be rewritten, so censor becomes block there");
        assertEquals("§cPlease do not write in capital letters.", f.platform.sentTo(bob).get(0));

        set(SpamSettings.CAPS_MAX, 100);
        assertTrue(chat("STOP SHOUTING AT ME").allowed());
        set(SpamSettings.CAPS_MAX, 50);
        set(SpamSettings.CAPS_MIN_LENGTH, 3);
        set(SpamSettings.CAPS.action(), "block");
        assertTrue(chat("LOL").blocked());
    }

    @Test
    void characterAndSymbolFlood() {
        only(SpamSettings.FLOOD);
        Verdict run = chat("heeeeeeeeeello woooooooooorld");
        assertTrue(run.censored());
        assertEquals("heeello wooorld", run.text(), "runs longer than 6 are cut to collapse-to (3)");
        assertEquals("character flood", run.primary().detail());
        assertTrue(chat("hello!!!!!! cooool").allowed(), "6 in a row is still allowed");
        assertTrue(chat("the number is 1000000000").allowed(), "digits are not a flood");
        assertTrue(chat("ok          then").allowed(), "spaces are not a flood");
        assertEquals("😂😂😂 funny", chat("😂😂😂😂😂😂😂😂😂 funny").text(), "emoji count as characters, not as halves");

        Verdict symbols = chat("!?!?#@$%&*()_+=<>~");
        assertTrue(symbols.blocked(), "mostly symbols: nothing sensible to keep, so even 'censor' blocks");
        assertEquals("symbol flood", symbols.primary().detail());
        assertTrue(chat(":) <3").allowed(), "short");
        assertTrue(chat("wow!!! really??? ok... :)").allowed(), "normal punctuation");
        Verdict both = chat("!!!!!!!!!!!!!!!!!!!!!!!!!!");
        assertEquals("!!!", both.text(), "one long run is simply shortened");
        assertTrue(chat("!!!!!!!!!!!!??????????!!!!!!!!!!!!@@@@@@@@@@@@").blocked(), "still mostly symbols after shortening");

        set(SpamSettings.FLOOD_MAX_SYMBOLS, 100);
        assertTrue(chat("!?!?#@$%&*()_+=<>~").allowed());
        set(SpamSettings.FLOOD_MAX_RUN, 3);
        set(SpamSettings.FLOOD_COLLAPSE, 1);
        assertEquals("helo", chat("hellllo").text());
        set(SpamSettings.FLOOD.action(), "log-only");
        assertTrue(chat("hellllo").allowed());
    }

    @Test
    void repeatedWords() {
        only(SpamSettings.WORDS);
        assertTrue(chat("buy buy buy buy now").allowed(), "4 times is allowed");
        Verdict v = chat("Buy buy BUY buy, buy! now");
        assertTrue(v.blocked());
        assertEquals("repeated word 'buy'", v.primary().detail());
        assertEquals("§cPlease do not repeat the same word so often.", f.platform.sentTo(bob).get(0));
        assertTrue(chat("a a a a a a a a").allowed(), "words shorter than min-word-length are not counted");
        set(SpamSettings.WORDS.action(), "censor");
        set(SpamSettings.WORDS_MAX, 2);
        assertEquals("go go now", chat("go go go go now go").text());
    }

    @Test
    void commandSpamCountsEveryCommandAlsoUnscannedOnes() {
        only(SpamSettings.COMMANDS);
        set(SpamSettings.COMMANDS_MAX, 3);
        set(SpamSettings.COMMANDS_EXEMPT, List.of("/Ping"));
        for (String command : List.of("/home", "/spawn", "/msg Amy hi")) {
            assertTrue(f.say(bob, Message.COMMAND, command).allowed(), command);
        }
        for (int i = 0; i < 10; i++) {
            assertTrue(f.say(bob, Message.COMMAND, "/ping").allowed(), "exempt commands never count");
        }
        Verdict v = f.say(bob, Message.COMMAND, "/home");
        assertTrue(v.blocked(), "a bare command that is never scanned for words still counts");
        assertEquals("spam-commands", v.primary().check());
        assertEquals("§cYou are using commands too fast. Please slow down.", f.platform.sentTo(bob).get(0));
        assertEquals("/home", events.get(0).data().get("text"));
        assertTrue(chat("chat is counted separately").allowed());

        seconds(6);
        assertTrue(f.say(bob, Message.COMMAND, "/home").allowed());
        // also when command scanning for words is switched off
        set(CoreSettings.SCAN_COMMANDS, false);
        seconds(6);
        for (int i = 0; i < 3; i++) {
            assertTrue(f.say(bob, Message.COMMAND, "/msg Amy fuck").allowed(), "words are not scanned now");
        }
        assertTrue(f.say(bob, Message.COMMAND, "/msg Amy hi").blocked(), "but the command counter still works");
        // and a blocked word in a scanned command is still found first
        set(CoreSettings.SCAN_COMMANDS, true);
        seconds(6);
        assertEquals("badword", f.say(bob, Message.COMMAND, "/msg Amy fuck").primary().check());
    }

    @Test
    void rulesWorkTogetherAndCleanedTextIsHandedOn() {
        set(SpamSettings.ENABLED, true);
        Verdict v = chat("HEEEEEEEEEELLO EVERYBODY OUT THERE");
        assertTrue(v.censored());
        assertEquals(List.of("spam-caps", "spam-flood"), v.hits().stream().map(Hit::check).toList());
        assertEquals("heeello everybody out there", v.text(), "caps lower-cased it, flood then shortened the run");
        assertEquals(List.of("spam", "spam", "violation"), events.stream().map(ModerationEvent::kind).toList());
        // a bad word still wins, and is found before the spam rules run
        assertEquals("badword", chat("FUUUUUUUUUUUUCK THIS STUPID GAME").primary().check());
        // the master switch
        set(SpamSettings.ENABLED, false);
        assertFalse(chat("HEEEEEEEEEELLO EVERYBODY OUT THERE").hasHits());
    }

    @Test
    void memoryIsBoundedAndCleanedOnQuitAndExpiry() {
        only(SpamSettings.RATE);
        set(SpamSettings.MAX_TRACKED, 10);
        set(SpamSettings.FORGET_AFTER, Duration.ofSeconds(60));
        List<UUID> players = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            UUID id = f.platform.join("P" + i);
            players.add(id);
            f.say(id, Message.CHAT, "hello " + i);
            seconds(0.01);
            assertTrue(engine.spamState().size() <= 10, "never more than max-tracked-players: " + engine.spamState().size());
        }
        assertEquals(10, engine.spamState().size());

        engine.onQuit(players.get(49));
        assertEquals(9, engine.spamState().size(), "a player who leaves is forgotten");

        seconds(61);
        f.say(players.get(0), Message.CHAT, "anyone here?");
        assertEquals(1, engine.spamState().size(), "idle players are forgotten after forget-after");

        // per-player lists are bounded too: a million messages do not grow the memory
        SpamState state = new SpamState();
        UUID id = UUID.randomUUID();
        for (int i = 0; i < 100_000; i++) {
            assertTrue(state.hit(id, SpamState.Counter.MESSAGES, 1000 + i, 1_000_000_000L, 6) <= 6);
            assertTrue(state.repeats(id, "m" + (i % 3), 1000 + i, 1_000_000_000L, 1.0, 5) <= 5);
        }
        engine.shutdown();
        assertEquals(0, engine.spamState().size());
    }

    @Test
    void profilesCanOverrideSpamSettingsPerWorld() {
        only(SpamSettings.CAPS);
        TestFiles.write(f.file("worlds.yml"), """
            default-profile: default
            use-permission-profiles: true
            profiles:
              default: {}
              loud:
                filters.spam.caps.enabled: false
              strict:
                filters:
                  spam:
                    caps:
                      max-percent: 10
                      action: block
                      message: "&4No capitals in the lobby."
            worlds:
              "arena_*": loud
              lobby: strict
            """);
        assertTrue(engine.reload().isEmpty(), engine.problems().toString());
        assertTrue(chat("THIS IS THE NORMAL WORLD").censored());
        f.platform.worlds.put(bob, "arena_1");
        assertFalse(chat("THIS IS THE ARENA YEAH").hasHits());
        f.platform.worlds.put(bob, "lobby");
        assertTrue(chat("Hello Everyone In The Lobby").blocked());
        assertEquals("§4No capitals in the lobby.", f.platform.sentTo(bob).get(0));
        assertEquals("strict", events.get(events.size() - 1).data().get("profile"));
    }

    @Test
    void invalidValuesAreNamedAndReplacedBySafeOnes() {
        set(SpamSettings.ENABLED, true);
        TestFiles.edit(f.file("filters/spam.yml"), "max-messages: 5", "max-messages: lots");
        TestFiles.edit(f.file("filters/spam.yml"), "window: \"5s\"\n    repeat:", "window: \"soon\"\n    repeat:");
        TestFiles.edit(f.file("filters/spam.yml"), "max-percent: 70", "max-percent: 700");
        TestFiles.edit(f.file("filters/spam.yml"), "action: censor\n      # Sent to the player when the message was blocked by this rule. "
            + "Empty = no message. Colours: & codes.\n      message: \"&cPlease do not write", "action: explode\n      # Sent to the player when the "
            + "message was blocked by this rule. Empty = no message. Colours: & codes.\n      message: \"&cPlease do not write");
        List<String> problems = engine.reload().stream().map(Object::toString).toList();
        assertEquals(4, problems.size(), problems.toString());
        assertTrue(problems.stream().anyMatch(p -> p.contains("filters/spam.yml") && p.contains("filters.spam.rate.max-messages") && p.contains("'lots'")), problems.toString());
        assertTrue(problems.stream().anyMatch(p -> p.contains("filters.spam.rate.window") && p.contains("'soon'")), problems.toString());
        assertTrue(problems.stream().anyMatch(p -> p.contains("filters.spam.caps.max-percent") && p.contains("using 100")), problems.toString());
        assertTrue(problems.stream().anyMatch(p -> p.contains("filters.spam.caps.action") && p.contains("one of: block, censor, log-only")), problems.toString());
        assertEquals(5, engine.settings().get(SpamSettings.RATE_MAX));
        assertEquals("censor", engine.settings().get(SpamSettings.CAPS.action()));
    }

    @Test
    void similarityMatchesPlainEditDistance() {
        Random random = new Random(11);
        for (int i = 0; i < 20_000; i++) {
            String a = randomText(random);
            String b = random.nextInt(3) == 0 ? randomText(random) : mutate(random, a);
            double ratio = new double[] {0.5, 0.7, 0.85, 0.95, 1.0}[random.nextInt(5)];
            int longer = Math.max(a.length(), b.length());
            boolean expected = a.equals(b) || (ratio < 1.0 && longer > 0 && levenshtein(a, b) <= (int) Math.floor((1.0 - ratio) * longer + 1e-9));
            assertEquals(expected, Similarity.similar(a, b, ratio), a + " | " + b + " | " + ratio);
        }
        assertEquals("hello world", Similarity.canonical("  HeLLo \t  WORLD  "));
        assertEquals(256, Similarity.canonical("x".repeat(5000)).length(), "long messages are compared by their first 256 characters");
    }

    private static String randomText(Random random) {
        StringBuilder out = new StringBuilder();
        for (int n = random.nextInt(24); n > 0; n--) {
            out.append("abc d".charAt(random.nextInt(5)));
        }
        return out.toString();
    }

    private static String mutate(Random random, String text) {
        StringBuilder out = new StringBuilder(text);
        for (int n = random.nextInt(4); n > 0; n--) {
            int at = out.length() == 0 ? 0 : random.nextInt(out.length() + 1);
            switch (random.nextInt(3)) {
                case 0 -> out.insert(at, 'x');
                case 1 -> {
                    if (at < out.length()) {
                        out.deleteCharAt(at);
                    }
                }
                default -> {
                    if (at < out.length()) {
                        out.setCharAt(at, 'y');
                    }
                }
            }
        }
        return out.toString();
    }

    private static int levenshtein(String a, String b) {
        int[][] d = new int[a.length() + 1][b.length() + 1];
        for (int i = 0; i <= a.length(); i++) {
            for (int j = 0; j <= b.length(); j++) {
                d[i][j] = i == 0 ? j : j == 0 ? i : Math.min(Math.min(d[i - 1][j], d[i][j - 1]) + 1,
                    d[i - 1][j - 1] + (a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1));
            }
        }
        return d[a.length()][b.length()];
    }
}
