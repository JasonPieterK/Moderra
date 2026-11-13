package com.jason.plugins.antibadwords.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jason.plugins.antibadwords.engine.config.Setting;
import com.jason.plugins.antibadwords.engine.filters.LinkParser;
import com.jason.plugins.antibadwords.engine.filters.LinkSettings;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The link parser (many ways of hiding a link) and the link policy. */
class LinkFilterTest {
    private static final LinkParser.Options OPTIONS = new LinkParser.Options(new HashSet<>(LinkSettings.ENDINGS.defaultValue()),
        LinkSettings.DOT_WORDS.defaultValue(), true);

    @TempDir
    Path dir;
    Fixture f;
    Engine engine;
    UUID bob;

    @BeforeEach
    void setUp() {
        f = new Fixture(dir);
        engine = f.start();
        bob = f.platform.join("Bob");
        f.platform.rewritable.add(Message.CHAT);
    }

    private <T> void set(Setting<T> setting, T value) {
        try {
            engine.set(setting, value);
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
        assertTrue(engine.problems().isEmpty(), engine.problems().toString());
    }

    private static List<String> hosts(String text) {
        return LinkParser.find(text, OPTIONS).stream().map(l -> l.host() + l.path()).toList();
    }

    private static void finds(String expected, String text) {
        assertEquals(List.of(expected), hosts(text), text);
    }

    @Test
    void plainLinks() {
        finds("example.com", "join example.com now");
        finds("example.com", "join Example.COM now");
        finds("example.com", "http://example.com");
        finds("example.com/path?x=1#top", "see https://www.example.com/path?x=1#top please");
        finds("example.com", "www.example.com");
        finds("something.example", "http://something.example");
        finds("localhost", "http://localhost:8080");
        finds("play.myserver.net", "ip: play.myserver.net:25565");
        finds("my-server.co.uk", "(my-server.co.uk)");
        finds("example.com", "mail me: bob@example.com.");
        finds("example.org", "\"example.org\", he said");
        finds("example.com", "hxxp://example.com");
        finds("bücher.com", "bücher.com is nice");
        finds("xn--bcher-kva.com", "xn--bcher-kva.com");
        assertEquals(List.of("a.com", "b.net/x"), hosts("a.com and b.net/x"));
        LinkParser.Link link = LinkParser.find("go to https://example.com/a now", OPTIONS).get(0);
        assertEquals("https://example.com/a", "go to https://example.com/a now".substring(link.start(), link.end()));
        assertEquals(LinkParser.Kind.DOMAIN, link.kind());
        assertFalse(link.obfuscated());
    }

    @Test
    void hiddenDotsAndOtherObfuscation() {
        for (String text : new String[] {
            "example dot com", "example DOT com", "example  dot  com", "example(dot)com", "example (dot) com", "example[dot]com",
            "example [ dot ] com", "example{dot}com", "example<dot>com", "example[.]com", "example(.)com", "example [.] com",
            "example .com", "example . com", "example  .  com", "example。com", "example．com", "example｡com", "example․com", "example•com",
            "example·com", "ｅｘａｍｐｌｅ．ｃｏｍ", "exam​ple.com", "example.​com", "example­.com", "example d0t com",
            "example punkt com", "example точка com", "EXAMPLE[DOT]COM", "𝐞𝐱𝐚𝐦𝐩𝐥𝐞.𝐜𝐨𝐦"}) {
            finds("example.com", "visit " + text + " today");
        }
        finds("play.example.com", "play dot example dot com");
        finds("play.example.com", "play(.)example(dot)com");
        finds("mple.com", "exa mple . com");
        finds("example.com/shop", "example dot com/shop");
        assertTrue(LinkParser.find("example dot com", OPTIONS).get(0).obfuscated());
        String text = "go example (dot) com now";
        LinkParser.Link link = LinkParser.find(text, OPTIONS).get(0);
        assertEquals("example (dot) com", text.substring(link.start(), link.end()), "the whole hidden link is marked");

        LinkParser.Options strict = new LinkParser.Options(OPTIONS.endings(), OPTIONS.dotWords(), false);
        assertTrue(LinkParser.find("example dot com and example[.]com and example .com", strict).isEmpty(), "detect-hidden-dots off");
        assertEquals(1, LinkParser.find("example。com", strict).size(), "unicode dots are always read");
    }

    @Test
    void ipAddresses() {
        finds("203.0.113.5", "join 203.0.113.5 now");
        finds("203.0.113.5", "203.0.113.5:25565");
        finds("203.0.113.5", "203 . 0 . 113 . 5".replace(" . ", "."));
        finds("203.0.113.5", "203[.]0[.]113[.]5");
        finds("203.0.113.5", "203(dot)0(dot)113(dot)5");
        finds("192.168.1.1/admin", "http://192.168.1.1/admin");
        assertEquals(LinkParser.Kind.IPV4, LinkParser.find("203.0.113.5", OPTIONS).get(0).kind());
        finds("2001:db8::8a2e:370:7334", "v6: 2001:db8::8a2e:370:7334 ok");
        finds("2001:db8:85a3:0:0:8a2e:370:7334", "2001:DB8:85a3:0:0:8a2e:370:7334");
        finds("fe80::1ff:fe23:4567:890a", "[fe80::1ff:fe23:4567:890a]:25565");
        assertEquals(LinkParser.Kind.IPV6, LinkParser.find("2001:db8::1", OPTIONS).get(0).kind());
    }

    @Test
    void thingsThatAreNotLinks() {
        for (String text : new String[] {
            "hello world", "that was fun. com on, lets go", "wait... what", "e.g. this", "i.e. that", "version 1.21.11 is out", "v1.2.3.4",
            "1.2.3", "300.1.2.3", "1.2.3.4.5", "3.14 is pi", "price: 10.50", "file.txt and readme.md and photo.png", "player.name",
            "hello.world", "hello.planet", "end of sentence.Next one", "ok.so what", "trust.me bro", "casa.de papel", "it is 12:30:45", "aa:bb:cc:dd:ee:ff",
            "std::string and a::b", "::1", ":) :: :D", "smile :) dot", "a dot b", "the dot is small", "connect the dot com-pletely?",
            "what . is . this", "no. com", "x . y", "www", "http://", "://", "...", "a.b", "com", ".com", "@.com", "-.-", "o.O", "T.T",
            "I like it. Net profit is good", "10/10", "and/or", "abc@def", "#hashtag", "key=value", "100% sure.", "Mr. Smith", "U.S.A."}) {
            assertEquals(List.of(), hosts(text), text);
        }
    }

    @Test
    void randomAndNastyInputNeverFailsAndStaysFast() {
        Random random = new Random(5);
        String[] pool = {"a", "b", "z", "0", "9", ".", ".", ":", ":", "/", " ", " ", "dot", "(", ")", "[", "]", "com", "www", "http", "-", "。",
            "​", "é", "😀", "@", "::", "f", "1"};
        long start = System.nanoTime();
        for (int i = 0; i < 40_000; i++) {
            StringBuilder text = new StringBuilder();
            for (int n = random.nextInt(60); n > 0; n--) {
                text.append(pool[random.nextInt(pool.length)]);
            }
            String t = text.toString();
            for (LinkParser.Link link : LinkParser.find(t, OPTIONS)) {
                assertTrue(link.start() >= 0 && link.start() < link.end() && link.end() <= t.length(), t);
                assertFalse(link.host().isEmpty(), t);
            }
        }
        for (String nasty : new String[] {".".repeat(5000), "a.".repeat(3000), ":".repeat(5000), "a:".repeat(3000), " . ".repeat(2000),
            "( dot ) ".repeat(800), "dot ".repeat(1500), "a dot ".repeat(1200), "http://".repeat(900), "a-".repeat(3000) + ".com",
            "1.".repeat(3000), "​".repeat(5000), "www.".repeat(1500)}) {
            LinkParser.find(nasty, OPTIONS);
        }
        long millis = (System.nanoTime() - start) / 1_000_000;
        assertTrue(millis < 20_000, "took " + millis + " ms");
    }

    @Test
    void offByDefaultThenBlockAll() {
        assertEquals("off", engine.settings().get(LinkSettings.MODE));
        assertTrue(engine.settings().get(LinkSettings.ALLOWED).isEmpty(), "the default allow-list is empty");
        assertFalse(f.say(bob, Message.CHAT, "join example.com or 203.0.113.5 or discord.gg/abc").hasHits());

        set(LinkSettings.MODE, "block-all");
        List<ModerationEvent> events = new ArrayList<>();
        engine.events().subscribe(events::add);
        Verdict v = f.say(bob, Message.CHAT, "join example dot com now");
        assertTrue(v.blocked());
        assertEquals("link", v.primary().check());
        assertEquals("link example.com", v.primary().detail());
        assertEquals(List.of("§cLinks are not allowed here."), f.platform.sentTo(bob));
        assertEquals(0, engine.strikes().get(bob));
        assertEquals(List.of("link", "violation"), events.stream().map(ModerationEvent::kind).toList());
        assertEquals("join example dot com now", events.get(0).data().get("text"));
        assertTrue(f.platform.infos.contains("Bob triggered link (link example.com) in chat: blocked"));

        assertTrue(f.say(bob, Message.CHAT, "hello there, nice base").allowed());
        assertTrue(f.say(bob, Message.SIGN, "example.com").blocked());
        assertTrue(f.say(bob, Message.BOOK, "see example.com").blocked());
        assertTrue(f.say(bob, Message.ANVIL, "example.com").blocked());
        assertTrue(f.say(bob, Message.COMMAND, "/msg Amy go to example.com").blocked());
        assertTrue(f.say(bob, Message.COMMAND, "/warp example.com").allowed(), "not a scanned command");

        UUID vip = f.platform.join("Vip", LinkSettings.PERMISSION_BYPASS);
        assertFalse(f.say(vip, Message.CHAT, "example.com").hasHits());
        set(LinkSettings.SOURCES, List.of("chat"));
        assertTrue(f.say(bob, Message.SIGN, "example.com").allowed());
    }

    @Test
    void allowListBlockListIpsAndInvites() {
        set(LinkSettings.MODE, "allow-list");
        set(LinkSettings.ALLOWED, List.of("https://www.MyServer.net/", "*.wiki.gg", ".org", "203.0.113.5", "youtu*.com"));
        for (String ok : new String[] {"myserver.net", "www.myserver.net/rules", "shop.myserver.net", "minecraft.wiki.gg/Creeper", "example.org",
            "203.0.113.5", "youtube.com/watch?v=1", "https://youtu-be.com", "myserver dot net"}) {
            assertFalse(f.say(bob, Message.CHAT, "look: " + ok).hasHits(), ok);
        }
        for (String bad : new String[] {"notmyserver.net", "myserver.net.evil.com", "wiki.gg", "example.com", "203.0.113.6", "2001:db8::1",
            "evil dot com", "myserver.net and evil.com"}) {
            assertTrue(f.say(bob, Message.CHAT, "look: " + bad).blocked(), bad);
        }
        // invites are caught even when their domain is allowed, unless the invite itself is allowed
        set(LinkSettings.ALLOWED, List.of("discord.com", "discord.gg"));
        set(LinkSettings.ALLOWED_INVITES, List.of("discord.gg/OurServer"));
        assertFalse(f.say(bob, Message.CHAT, "https://discord.com/channels/1/2").hasHits());
        Verdict invite = f.say(bob, Message.CHAT, "join discord.gg/abcDEF");
        assertTrue(invite.blocked());
        assertEquals("invite link discord.gg/abcdef", invite.primary().detail());
        assertTrue(f.say(bob, Message.CHAT, "https://discord.com/invite/xyz").blocked());
        assertTrue(f.say(bob, Message.CHAT, "discord (dot) gg/abc").blocked());
        assertTrue(f.say(bob, Message.CHAT, "t.me/somegroup").blocked(), "an invite host counts even though .me is not a known ending");
        assertFalse(f.say(bob, Message.CHAT, "join discord.gg/ourserver").hasHits());
        set(LinkSettings.BLOCK_INVITES, false);
        assertFalse(f.say(bob, Message.CHAT, "join discord.gg/abcDEF").hasHits(), "now it is just an allowed domain");

        set(LinkSettings.BLOCK_IPS, false);
        assertFalse(f.say(bob, Message.CHAT, "203.0.113.99 and 2001:db8::1").hasHits());

        set(LinkSettings.MODE, "block-list");
        set(LinkSettings.BLOCKED, List.of("evil.com", "*.bad.net"));
        set(LinkSettings.BLOCK_IPS, true);
        set(LinkSettings.BLOCK_INVITES, true);
        assertFalse(f.say(bob, Message.CHAT, "example.com is fine, bad.net too").hasHits());
        assertTrue(f.say(bob, Message.CHAT, "go to www.evil.com").blocked());
        assertTrue(f.say(bob, Message.CHAT, "go to x.bad.net").blocked());
        assertTrue(f.say(bob, Message.CHAT, "203.0.113.99").blocked(), "the IP switch works in every mode");
        assertTrue(f.say(bob, Message.CHAT, "discord.gg/abc").blocked(), "the invite switch works in every mode");
    }

    @Test
    void actionsCensorLogOnlyWeightAndEndings() {
        set(LinkSettings.MODE, "block-all");
        set(LinkSettings.ACTION, "censor");
        Verdict v = f.say(bob, Message.CHAT, "go to example (dot) com and https://evil.net/x now");
        assertTrue(v.censored());
        assertEquals("go to [link] and [link] now", v.text());
        set(LinkSettings.REPLACEMENT, "***");
        assertEquals("ip *** ok", f.say(bob, Message.CHAT, "ip 203.0.113.5:25565 ok").text());
        assertTrue(f.say(bob, Message.SIGN, "example.com").blocked(), "no rewriting on signs");

        set(LinkSettings.ACTION, "log-only");
        v = f.say(bob, Message.CHAT, "example.com");
        assertTrue(v.allowed() && v.hasHits());

        set(LinkSettings.ACTION, "block");
        set(LinkSettings.WEIGHT, 2);
        f.say(bob, Message.CHAT, "example.com");
        assertEquals(0, engine.strikes().get(bob), "2 strikes reached the limit of 2: punished and reset");
        assertFalse(f.platform.broadcasts.isEmpty());

        assertFalse(f.say(bob, Message.CHAT, "casa.de papel").hasHits());
        List<String> endings = new ArrayList<>(engine.settings().get(LinkSettings.ENDINGS));
        endings.add(".DE");
        set(LinkSettings.ENDINGS, endings);
        assertTrue(f.say(bob, Message.CHAT, "casa.de papel").blocked(), "the admin added the ending");
        set(LinkSettings.DOT_WORDS, List.of("bullet"));
        assertTrue(f.say(bob, Message.CHAT, "example bullet com").blocked());
        assertFalse(f.say(bob, Message.CHAT, "example dot com").hasHits());
        set(LinkSettings.DETECT_OBFUSCATED, false);
        assertFalse(f.say(bob, Message.CHAT, "example bullet com").hasHits());

        TestFiles.edit(f.file("filters/links.yml"), "mode: block-all", "mode: everything");
        assertTrue(engine.reload().stream().anyMatch(p -> p.toString().contains("filters.links.mode")
            && p.toString().contains("one of: off, block-all, allow-list, block-list")), engine.problems().toString());
        assertEquals("off", engine.settings().get(LinkSettings.MODE), "a wrong mode means off, not 'block everything'");
    }
}
