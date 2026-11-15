package com.jason.plugins.antibadwords.engine.discord;

import com.jason.plugins.antibadwords.engine.command.Chat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jason.plugins.antibadwords.engine.Engine;
import com.jason.plugins.antibadwords.engine.TestEngines;
import com.jason.plugins.antibadwords.engine.FakePlatform;
import com.jason.plugins.antibadwords.engine.ModerationEvent;
import com.jason.plugins.antibadwords.engine.command.CommandSender;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntFunction;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.yaml.snakeyaml.Yaml;

/** The Discord webhook sender against a local fake HTTP server. */
class DiscordWebhookTest {
    private static final String TOKEN = "SeCrEtToKeN-abcdef123456";
    private static final String PATH = "/api/webhooks/1234567/" + TOKEN;

    /** One request the fake server received. */
    record Hit(String path, String body, Map<String, List<String>> headers, long at) {
        @SuppressWarnings("unchecked")
        Map<String, Object> json() {
            return (Map<String, Object>) new Yaml().load(body);
        }
    }

    @TempDir
    Path dir;
    HttpServer server;
    final LinkedBlockingQueue<Hit> hits = new LinkedBlockingQueue<>();
    final AtomicInteger calls = new AtomicInteger();
    /** Decides the answer for the n-th call (1-based): an HTTP status; the handler may also block. */
    volatile IntFunction<Integer> answer = n -> 204;
    volatile String retryAfter = null;
    FakePlatform platform;
    Engine engine;
    String url;

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.setExecutor(Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r);
            t.setDaemon(true);
            return t;
        }));
        server.createContext("/", exchange -> {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            int n = calls.incrementAndGet();
            hits.add(new Hit(exchange.getRequestURI().getPath(), body, Map.copyOf(exchange.getRequestHeaders()), System.nanoTime()));
            int status = answer.apply(n);
            if (retryAfter != null && status == 429) {
                exchange.getResponseHeaders().add("Retry-After", retryAfter);
            }
            exchange.sendResponseHeaders(status, -1);
            exchange.close();
        });
        server.start();
        url = "http://127.0.0.1:" + server.getAddress().getPort() + PATH;
        platform = new FakePlatform(dir);
        engine = TestEngines.track(Engine.load(platform, dir));
    }

    @AfterEach
    void stop() {
        engine.shutdown();
        server.stop(0);
    }

    /** Switches Discord on with a fast sender (no batch wait, no gap, no backoff). */
    void configure() throws IOException {
        engine.set(DiscordSettings.ENABLED, true);
        engine.set(DiscordSettings.ALERTS_URL, url);
        engine.set(DiscordSettings.BATCH_WAIT, Duration.ZERO);
        engine.set(DiscordSettings.MIN_INTERVAL, Duration.ZERO);
        engine.set(DiscordSettings.BACKOFF, Duration.ZERO);
    }

    ModerationEvent punishment(String player) {
        return new ModerationEvent("punishment", 1_700_000_000_000L, UUID.randomUUID(), player, "world_nether", "chat",
            Map.of("word", "badword", "count", "4", "limit", "3", "punishments", "a mute (10m)", "step", "short-mute"));
    }

    Hit next() throws InterruptedException {
        Hit hit = hits.poll(5, TimeUnit.SECONDS);
        assertNotNull(hit, "no request arrived");
        return hit;
    }

    List<String> warnings() {
        synchronized (platform.warnings) {
            return new ArrayList<>(platform.warnings);
        }
    }

    // ------------------------------------------------------------------------------------------

    @Test
    void embedPayloadHasTemplateColourAndTimestamp() throws Exception {
        configure();
        engine.events().publish(punishment("Bob"));
        Hit hit = next();
        assertEquals(PATH, hit.path());
        Map<String, Object> json = hit.json();
        assertEquals("AntiBadWords", json.get("username"));
        @SuppressWarnings("unchecked")
        Map<String, Object> embed = ((List<Map<String, Object>>) json.get("embeds")).get(0);
        assertEquals("Bob received a penalty at 4 strikes. Step: short-mute. Action: a mute (10m).", embed.get("description"));
        assertEquals(0xE74C3C, embed.get("color"));
        assertEquals("2023-11-14T22:13:20Z", embed.get("timestamp"));
        assertEquals("AntiBadWords: punishment", embed.get("title"));
        assertTrue(hit.headers().toString().toLowerCase().contains("application/json"));
    }

    @Test
    void plainStyleCustomTemplateMentionAndNoStrayPings() throws Exception {
        configure();
        engine.set(DiscordSettings.STYLE, "plain");
        engine.set(DiscordSettings.MENTION, "<@&998877>");
        engine.set(DiscordSettings.template("punishment"), "{player} / {word} / {source} / {world} / {count} / {limit} / {action}");
        engine.events().publish(punishment("@everyone_Bob"));
        Map<String, Object> json = next().json();
        String content = (String) json.get("content");
        assertTrue(content.startsWith("<@&998877>\n**[punishment]** "), content);
        assertTrue(content.contains("@​everyone\\_Bob / badword / chat / world\\_nether / 4 / 3 / a mute (10m)"), content);
        @SuppressWarnings("unchecked")
        Map<String, Object> allowed = (Map<String, Object>) json.get("allowed_mentions");
        assertEquals(List.of(), allowed.get("parse"), "a player's name can never ping");
        assertEquals(List.of("998877"), allowed.get("roles"));
        assertNull(json.get("embeds"));
    }

    @Test
    void mentionOnlyForTheMentionKinds() throws Exception {
        configure();
        engine.set(DiscordSettings.MENTION, "<@&5>");
        engine.set(DiscordSettings.ROUTE_ALERTS, List.of("*"));
        engine.events().publish(new ModerationEvent("unmute", 1L, null, "Bob", "", "", Map.of()));
        Map<String, Object> json = next().json();
        assertNull(json.get("content"), "unmute does not mention");
    }

    @Test
    void routingSendsKindsToTheirWebhooks() throws Exception {
        configure();
        engine.set(DiscordSettings.LOG_URL, url.replace("/1234567/", "/7654321/"));
        engine.set(DiscordSettings.ROUTE_ALERTS, List.of("punishment"));
        engine.set(DiscordSettings.ROUTE_LOG, List.of("violation", "punishment"));
        engine.events().publish(new ModerationEvent("violation", 1L, null, "Bob", "w", "chat", Map.of("word", "x")));
        engine.events().publish(new ModerationEvent("spam", 1L, null, "Bob", "w", "chat", Map.of()));
        Hit logged = next();
        assertTrue(logged.path().contains("/7654321/"), "violation goes to the log webhook only");
        engine.events().publish(punishment("Bob"));
        Hit a = next();
        Hit b = next();
        assertEquals(2, List.of(a.path(), b.path()).stream().distinct().count(), "punishment goes to both");
        assertNull(hits.poll(300, TimeUnit.MILLISECONDS), "spam is routed nowhere");
    }

    @Test
    void nothingIsSentWhileDisabledOrOff() throws Exception {
        configure();
        engine.set(DiscordSettings.ENABLED, false);
        engine.events().publish(punishment("Bob"));
        engine.set(DiscordSettings.ENABLED, true);
        engine.set(DiscordSettings.MODE, "off");
        engine.events().publish(punishment("Bob"));
        engine.set(DiscordSettings.MODE, "discordsrv");
        engine.events().publish(punishment("Bob"));
        assertNull(hits.poll(400, TimeUnit.MILLISECONDS));
    }

    @Test
    void batchesSeveralEventsIntoOneRequest() throws Exception {
        configure();
        engine.set(DiscordSettings.BATCH_WAIT, Duration.ofSeconds(1));
        engine.set(DiscordSettings.BATCH_SIZE, 5);
        for (int i = 0; i < 3; i++) {
            engine.events().publish(punishment("P" + i));
        }
        Map<String, Object> json = next().json();
        assertEquals(3, ((List<?>) json.get("embeds")).size());
        assertNull(hits.poll(300, TimeUnit.MILLISECONDS));
    }

    @Test
    void batchSizeCapsTheRequest() throws Exception {
        configure();
        engine.set(DiscordSettings.BATCH_WAIT, Duration.ofMillis(300));
        engine.set(DiscordSettings.BATCH_SIZE, 2);
        for (int i = 0; i < 5; i++) {
            engine.events().publish(punishment("P" + i));
        }
        assertEquals(2, ((List<?>) next().json().get("embeds")).size());
        assertEquals(2, ((List<?>) next().json().get("embeds")).size());
        assertEquals(1, ((List<?>) next().json().get("embeds")).size());
    }

    @Test
    void minimumIntervalSeparatesRequests() throws Exception {
        configure();
        engine.set(DiscordSettings.MIN_INTERVAL, Duration.ofMillis(400));
        engine.set(DiscordSettings.BATCH_SIZE, 1);
        engine.events().publish(punishment("A"));
        engine.events().publish(punishment("B"));
        Hit first = next();
        Hit second = next();
        assertTrue(TimeUnit.NANOSECONDS.toMillis(second.at() - first.at()) >= 350, "gap was too short");
    }

    @Test
    void http429WaitsForRetryAfterThenSendsAgain() throws Exception {
        configure();
        answer = n -> n == 1 ? 429 : 204;
        retryAfter = "0.5";
        engine.events().publish(punishment("Bob"));
        Hit first = next();
        Hit second = next();
        assertEquals(first.body(), second.body(), "the same message is repeated");
        long waited = TimeUnit.NANOSECONDS.toMillis(second.at() - first.at());
        assertTrue(waited >= 450, "waited only " + waited + " ms");
        assertNull(hits.poll(300, TimeUnit.MILLISECONDS), "and only once more");
        assertEquals(1, engine.discord().status().stream().filter(l -> l.contains("sent 1")).count());
    }

    @Test
    void serverErrorsAreRetriedThenGivenUpWithAMaskedWarning() throws Exception {
        configure();
        engine.set(DiscordSettings.RETRIES, 2);
        answer = n -> 503;
        engine.events().publish(punishment("Bob"));
        next();
        next();
        next();
        assertNull(hits.poll(300, TimeUnit.MILLISECONDS), "1 try + 2 retries");
        long until = System.currentTimeMillis() + 3000;
        while (warnings().stream().noneMatch(w -> w.contains("not delivered")) && System.currentTimeMillis() < until) {
            Thread.sleep(20);
        }
        List<String> failed = warnings().stream().filter(w -> w.contains("not delivered")).toList();
        assertEquals(1, failed.size());
        assertTrue(failed.get(0).contains("HTTP 503"));
    }

    @Test
    void aRefusalIsNotRepeatedAndNeverShowsTheUrl() throws Exception {
        configure();
        answer = n -> 404;
        engine.events().publish(punishment("Bob"));
        next();
        assertNull(hits.poll(300, TimeUnit.MILLISECONDS), "404 is final");
        long until = System.currentTimeMillis() + 3000;
        while (warnings().stream().noneMatch(w -> w.contains("not delivered")) && System.currentTimeMillis() < until) {
            Thread.sleep(20);
        }
        assertTrue(warnings().stream().anyMatch(w -> w.contains("HTTP 404") && w.contains("deleted")));
        assertNoSecret(warnings());
        assertNoSecret(platform.infos);
    }

    @Test
    void unreachableServerIsRetriedAndTheErrorTextIsMasked() throws Exception {
        configure();
        engine.set(DiscordSettings.RETRIES, 1);
        engine.set(DiscordSettings.ALERTS_URL, "http://127.0.0.1:1" + PATH);
        engine.events().publish(punishment("Bob"));
        long until = System.currentTimeMillis() + 5000;
        while (warnings().stream().noneMatch(w -> w.contains("not delivered")) && System.currentTimeMillis() < until) {
            Thread.sleep(20);
        }
        assertTrue(warnings().stream().anyMatch(w -> w.contains("Could not reach Discord")));
        assertNoSecret(warnings());
    }

    void assertNoSecret(List<String> lines) {
        for (String line : lines) {
            assertFalse(line.contains(TOKEN), line);
            assertFalse(line.contains("1234567"), line);
        }
    }

    @Test
    void secretsAreMaskedInSettingsStatusAndTestOutput() throws Exception {
        configure();
        assertEquals("********", DiscordSettings.ALERTS_URL.display(url));
        assertEquals("(not set)", DiscordSettings.LOG_URL.display(""));
        Capture who = new Capture(true);
        engine.commands().execute(who, "abw", new String[] {"discord", "status"});
        engine.commands().execute(who, "abw", new String[] {"discord", "test"});
        who.waitFor(5);
        who.lines().forEach(l -> assertFalse(l.contains(TOKEN) || l.contains("127.0.0.1"), l));
        assertTrue(who.lines().stream().anyMatch(l -> l.contains("alerts webhook: set (********)")), who.lines().toString());
        assertTrue(who.lines().stream().anyMatch(l -> l.contains("Discord §falerts§7: OK")), who.lines().toString());
        assertEquals("a ******** b", Secrets.mask("a " + url + " b", List.of(url)));
        assertEquals("x ********", Secrets.mask("x https://discord.com/api/webhooks/1/abcdefgh", List.of()));
        assertEquals("fail ********/ok", Secrets.mask("fail " + TOKEN + "/ok", List.of(url)));
    }

    @Test
    void environmentIndirectionIsResolvedAndAMissingVariableIsReported() throws Exception {
        engine.discord().setEnvironment(name -> name.equals("ABW_URL") ? url : null);
        engine.set(DiscordSettings.ENABLED, true);
        engine.set(DiscordSettings.BATCH_WAIT, Duration.ZERO);
        engine.set(DiscordSettings.ALERTS_URL, "${ENV:ABW_URL}");
        engine.set(DiscordSettings.LOG_URL, "${ENV:ABW_MISSING}");
        engine.events().publish(punishment("Bob"));
        assertEquals(PATH, next().path());
        assertTrue(warnings().stream().anyMatch(w -> w.contains("environment variable ABW_MISSING is not set")));
        assertNoSecret(warnings());
        assertFalse(platform.infos.toString().contains(TOKEN));
    }

    @Test
    void warnsWhenTheFileIsWorldReadableButOnlyForAWrittenOutUrl() throws Exception {
        configure();
        Path file = dir.resolve(DiscordSettings.FILE);
        Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-r--r--"));
        platform.warnings.clear();
        engine.reload();
        assertTrue(warnings().stream().anyMatch(w -> w.contains("discord.yml can be read by every user")), warnings().toString());
        assertNoSecret(warnings());

        Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-------"));
        platform.warnings.clear();
        engine.reload();
        assertTrue(warnings().stream().noneMatch(w -> w.contains("can be read by every user")));

        engine.discord().setEnvironment(name -> url);
        engine.set(DiscordSettings.ALERTS_URL, "${ENV:X}");
        Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-r--r--"));
        platform.warnings.clear();
        engine.reload();
        assertTrue(warnings().stream().noneMatch(w -> w.contains("can be read by every user")), "no URL in the file, nothing to protect");
    }

    @Test
    void fullQueueDropsTheOldestWithOneWarningAndNeverBlocksTheCaller() throws Exception {
        configure();
        engine.set(DiscordSettings.QUEUE_SIZE, 10);
        engine.set(DiscordSettings.BATCH_SIZE, 1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch firstSeen = new CountDownLatch(1);
        // the fake Discord hangs on the first request
        server.removeContext("/");
        server.createContext("/", exchange -> {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            hits.add(new Hit("/", body, Map.of(), System.nanoTime()));
            firstSeen.countDown();
            try {
                release.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException ignored) {
                // test is over
            }
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
        });
        engine.events().publish(punishment("P0"));
        assertTrue(firstSeen.await(5, TimeUnit.SECONDS));
        long t0 = System.nanoTime();
        for (int i = 1; i <= 29; i++) {
            engine.events().publish(punishment("P" + i));
        }
        long ms = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - t0);
        assertTrue(ms < 1000, "publishing took " + ms + " ms while Discord hangs");
        assertEquals(10, queuedAlerts());
        assertEquals(19, droppedAlerts());
        List<String> warns = warnings().stream().filter(w -> w.contains("dropping the oldest")).toList();
        assertEquals(1, warns.size(), "one warning, not one per message");
        assertNoSecret(warns);
        release.countDown();
        List<String> seen = new ArrayList<>();
        for (int i = 0; i < 11; i++) {
            seen.add(next().body());
        }
        assertTrue(seen.get(0).contains("P0 received"), "the one in flight was kept");
        assertTrue(seen.get(1).contains("P20 received"), "the oldest waiting ones were dropped");
        assertTrue(seen.get(10).contains("P29 received"));
        assertTrue(seen.stream().noneMatch(b -> b.contains("P5 received")));
    }

    int queuedAlerts() {
        return statusNumber("waiting ");
    }

    int droppedAlerts() {
        return statusNumber("dropped ");
    }

    private int statusNumber(String key) {
        String line = engine.discord().status().stream().filter(l -> l.startsWith("alerts webhook")).findFirst().orElseThrow();
        String rest = line.substring(line.indexOf(key) + key.length());
        return Integer.parseInt(rest.replaceAll("^(\\d+).*$", "$1"));
    }

    @Test
    void shutdownSendsWhatIsWaitingAndThenIgnoresNewEvents() throws Exception {
        configure();
        engine.set(DiscordSettings.BATCH_WAIT, Duration.ofSeconds(60));
        engine.set(DiscordSettings.BATCH_SIZE, 10);
        for (int i = 0; i < 3; i++) {
            engine.events().publish(punishment("P" + i));
        }
        assertNull(hits.poll(300, TimeUnit.MILLISECONDS), "still collecting");
        long t0 = System.nanoTime();
        engine.shutdown();
        assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - t0) < 4000);
        Hit hit = hits.poll(1, TimeUnit.SECONDS);
        assertNotNull(hit, "flushed on shutdown");
        assertEquals(3, ((List<?>) hit.json().get("embeds")).size());
        engine.events().publish(punishment("Late"));
        assertNull(hits.poll(300, TimeUnit.MILLISECONDS));
    }

    @Test
    void shutdownDoesNotWaitForRetriesOrRateLimits() throws Exception {
        configure();
        engine.set(DiscordSettings.BACKOFF, Duration.ofSeconds(30));
        engine.set(DiscordSettings.RETRIES, 5);
        answer = n -> 503;
        engine.events().publish(punishment("Bob"));
        next();
        long t0 = System.nanoTime();
        engine.shutdown();
        assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - t0) < 4000, "shutdown waited for the backoff");
    }

    @Test
    void testCommandNeedsItsPermissionAndReportsAFailure() throws Exception {
        configure();
        Capture nobody = new Capture(false);
        engine.commands().execute(nobody, "abw", new String[] {"discord", "test"});
        assertEquals(List.of(Chat.error("No permission.")), nobody.lines());
        assertEquals(0, calls.get());

        answer = n -> 401;
        Capture admin = new Capture(true);
        engine.commands().execute(admin, "abw", new String[] {"discord", "test"});
        admin.waitFor(2);
        assertTrue(admin.lines().stream().anyMatch(l -> l.contains("HTTP 401")), admin.lines().toString());
        assertNoSecret(admin.lines());

        engine.set(DiscordSettings.ALERTS_URL, "");
        Capture empty = new Capture(true);
        engine.commands().execute(empty, "abw", new String[] {"discord", "test"});
        assertTrue(empty.lines().get(0).contains("No usable webhook URL"));
        assertEquals(List.of("test", "status"), engine.commands().complete(admin, new String[] {"discord", ""}));
    }

    @Test
    void settingsLiveInDiscordYmlAndTheUrlsAreSecrets() {
        assertEquals(DiscordSettings.FILE, DiscordSettings.ALERTS_URL.file());
        assertEquals(com.jason.plugins.antibadwords.engine.config.SettingType.SECRET, DiscordSettings.ALERTS_URL.type());
        assertEquals(com.jason.plugins.antibadwords.engine.config.SettingType.SECRET, DiscordSettings.LOG_URL.type());
        assertEquals("auto", engine.value(DiscordSettings.MODE));
        assertFalse(engine.value(DiscordSettings.ENABLED));
        for (String kind : DiscordSettings.KINDS) {
            assertNotNull(DiscordSettings.color(kind));
            assertNotNull(DiscordSettings.template(kind));
        }
    }

    /** A command sender that records what it is told (thread safe). */
    static final class Capture implements CommandSender {
        private final boolean allowed;
        private final List<String> lines = new CopyOnWriteArrayList<>();

        Capture(boolean allowed) {
            this.allowed = allowed;
        }

        @Override
        public String name() {
            return "CONSOLE";
        }

        @Override
        public UUID id() {
            return null;
        }

        @Override
        public boolean has(String permission) {
            return allowed;
        }

        @Override
        public void send(String message) {
            lines.add(message);
        }

        List<String> lines() {
            return lines;
        }

        void waitFor(int count) throws InterruptedException {
            long until = System.currentTimeMillis() + 5000;
            while (lines.size() < count && System.currentTimeMillis() < until) {
                Thread.sleep(20);
            }
        }
    }
}
