package com.jason.plugins.moderra.engine.discord;

import com.jason.plugins.moderra.engine.EngineLog;
import com.jason.plugins.moderra.engine.moderation.DataFile;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Sends messages to ONE webhook from its own background thread. {@link #enqueue(Item)} never blocks and never does network
 * work: the queue is bounded and drops the oldest waiting message (with a warning) when it is full. The worker collects up
 * to {@code batchSize} messages into one request, keeps {@code minInterval} between requests, obeys HTTP 429
 * {@code Retry-After}, and retries failures with a doubling delay. {@link #shutdown(long)} sends what is waiting once, at once.
 * Nothing here ever writes the URL anywhere: every log line and every result text goes through {@link Secrets#mask}.
 */
final class WebhookSender {
    /** What the sender needs to know right now (read again for every batch, so a reload takes effect at once). */
    record Options(String username, int queueSize, long minIntervalMs, int batchSize, long batchWaitMs, long timeoutMs, int retries,
                   long backoffMs) {
    }

    /**
     * One message, already formatted.
     *
     * @param embed   the embed object (embed style) or {@code null}
     * @param line    the text line (plain style) or {@code null}
     * @param mention what to put in front ("" = nobody)
     * @param result  completed with a short, masked result text when this message was tried (tests and {@code /moderra discord test}); may be null
     */
    record Item(String kind, Map<String, Object> embed, String line, String mention, CompletableFuture<String> result) {
    }

    private static final Pattern RETRY_AFTER_JSON = Pattern.compile("\"retry_after\"\\s*:\\s*([0-9]+(?:\\.[0-9]+)?)");
    private static final long MAX_RATE_WAIT_MS = 60_000;
    private static final long DROP_WARN_EVERY_NANOS = 30_000_000_000L;
    private static final int CONTENT_LIMIT = 2000;

    private final String name;
    private final Supplier<String> url;
    private final Supplier<Options> options;
    private final EngineLog log;
    private final Supplier<Set<String>> secrets;
    private final HttpClient client;

    private final Object lock = new Object();
    private final Deque<Item> queue = new ArrayDeque<>();
    private Thread worker;
    private boolean stopping;
    private long nextAllowedNanos;
    private long dropped;
    private long lastDropWarnNanos;
    private long warnedDropped;
    private volatile long sent;
    private volatile long failed;
    private volatile String lastError = "";

    WebhookSender(String name, Supplier<String> url, Supplier<Options> options, EngineLog log, Supplier<Set<String>> secrets) {
        this.name = name;
        this.url = url;
        this.options = options;
        this.log = log;
        this.secrets = secrets;
        this.client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).followRedirects(HttpClient.Redirect.NEVER).build();
    }

    // ------------------------------------------------------------------------------------------
    // Caller side (any thread, never blocks)
    // ------------------------------------------------------------------------------------------

    /** Adds a message; when the queue is full the oldest waiting one is dropped. A {@code urgent} message goes to the front. */
    void enqueue(Item item, boolean urgent) {
        int capacity = Math.max(1, options.get().queueSize());
        Item droppedItem = null;
        boolean warn = false;
        long total = 0;
        synchronized (lock) {
            if (stopping) {
                if (item.result() != null) {
                    item.result().complete("Not sent: the server is shutting down.");
                }
                return;
            }
            while (queue.size() >= capacity) {
                droppedItem = urgent ? queue.pollLast() : queue.pollFirst();
                dropped++;
                if (droppedItem != null && droppedItem.result() != null) {
                    droppedItem.result().complete("Dropped: the queue was full.");
                }
            }
            if (urgent) {
                queue.addFirst(item);
            } else {
                queue.addLast(item);
            }
            if (droppedItem != null) {
                long now = System.nanoTime();
                if (warnedDropped == 0 || now - lastDropWarnNanos >= DROP_WARN_EVERY_NANOS) {
                    warn = true;
                    total = dropped;
                    lastDropWarnNanos = now;
                    warnedDropped = dropped;
                }
            }
            if (worker == null) {
                worker = new Thread(this::run, "Moderra-Discord-" + name);
                worker.setDaemon(true);
                worker.start();
            }
            lock.notifyAll();
        }
        if (warn) {
            warn("The Discord queue for the " + name + " webhook is full (" + capacity + " waiting): dropping the oldest messages. "
                + total + " dropped so far. Discord is too slow or unreachable; raise discord.queue.size or route fewer kinds.");
        }
    }

    int queued() {
        synchronized (lock) {
            return queue.size();
        }
    }

    long dropped() {
        synchronized (lock) {
            return dropped;
        }
    }

    long sent() {
        return sent;
    }

    long failed() {
        return failed;
    }

    String lastError() {
        return lastError;
    }

    /**
     * Stops accepting messages, sends what is waiting (one attempt per batch, no waiting for rate limits or retries) and
     * waits at most {@code waitMs} for the worker. Whatever is left after that is dropped with a warning.
     */
    void shutdown(long waitMs) {
        Thread thread;
        synchronized (lock) {
            stopping = true;
            thread = worker;
            lock.notifyAll();
        }
        if (thread == null) {
            return;
        }
        try {
            thread.join(Math.max(1, waitMs));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
        if (thread.isAlive()) {
            thread.interrupt();
            int left = queued();
            warn("Discord shutdown: gave up after " + waitMs + " ms with " + left + " messages for the " + name + " webhook not sent.");
        }
    }

    // ------------------------------------------------------------------------------------------
    // Worker
    // ------------------------------------------------------------------------------------------

    private void run() {
        try {
            while (true) {
                List<Item> batch = nextBatch();
                if (batch == null) {
                    return;
                }
                deliver(batch);
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        } catch (RuntimeException ex) {
            warn("The Discord sender for the " + name + " webhook stopped because of an error: " + ex);
        }
    }

    /** Waits for work, lets a batch fill up, honours the minimum interval; {@code null} = stop. */
    private List<Item> nextBatch() throws InterruptedException {
        Options o = options.get();
        synchronized (lock) {
            while (queue.isEmpty()) {
                if (stopping) {
                    return null;
                }
                lock.wait();
            }
            long fillUntil = System.nanoTime() + o.batchWaitMs() * 1_000_000L;
            while (!stopping && queue.size() < o.batchSize() && !hasUrgent()) {
                long remaining = fillUntil - System.nanoTime();
                if (remaining <= 0) {
                    break;
                }
                lock.wait(Math.max(1, remaining / 1_000_000L));
            }
            while (!stopping) {
                long remaining = nextAllowedNanos - System.nanoTime();
                if (remaining <= 0) {
                    break;
                }
                lock.wait(Math.max(1, remaining / 1_000_000L));
            }
            List<Item> batch = new ArrayList<>();
            while (batch.size() < o.batchSize() && !queue.isEmpty()) {
                batch.add(queue.pollFirst());
            }
            return batch;
        }
    }

    private boolean hasUrgent() {
        Item first = queue.peekFirst();
        return first != null && first.result() != null;
    }

    private void deliver(List<Item> batch) throws InterruptedException {
        Options o = options.get();
        String body = payload(batch, o.username());
        String result = "";
        boolean ok = false;
        for (int attempt = 0; ; attempt++) {
            boolean last = attempt >= o.retries() || isStopping();
            String target = url.get();
            if (target == null || target.isBlank()) {
                result = "No webhook URL is set for the " + name + " webhook.";
                break;
            }
            long wait;
            try {
                HttpResponse<String> response = client.send(request(target, body, o.timeoutMs()), HttpResponse.BodyHandlers.ofString());
                int status = response.statusCode();
                if (status >= 200 && status < 300) {
                    ok = true;
                    result = "OK (HTTP " + status + ")";
                    noteRateLimit(response);
                    break;
                }
                if (status == 429) {
                    wait = Math.min(MAX_RATE_WAIT_MS, retryAfterMs(response));
                    result = "Discord says slow down (HTTP 429, retry after " + wait + " ms)";
                    synchronized (lock) {
                        nextAllowedNanos = Math.max(nextAllowedNanos, System.nanoTime() + wait * 1_000_000L);
                    }
                } else if (status >= 500) {
                    wait = o.backoffMs() << Math.min(attempt, 20);
                    result = "Discord had a problem (HTTP " + status + ")";
                } else {
                    result = "Discord refused the message (HTTP " + status + "). "
                        + (status == 404 || status == 401 ? "The webhook URL is wrong or the webhook was deleted." : "Check the webhook and the settings.");
                    break;
                }
            } catch (IOException ex) {
                wait = o.backoffMs() << Math.min(attempt, 20);
                result = "Could not reach Discord: " + ex.getClass().getSimpleName() + (ex.getMessage() == null ? "" : " " + ex.getMessage());
            } catch (IllegalArgumentException ex) {
                result = "The webhook URL is not valid.";
                break;
            }
            if (last) {
                break;
            }
            sleep(wait);
        }
        result = mask(result);
        if (ok) {
            sent += batch.size();
        } else {
            failed += batch.size();
            lastError = result;
            warn("Discord " + name + " webhook: " + batch.size() + " message(s) not delivered. " + result);
        }
        for (Item item : batch) {
            if (item.result() != null) {
                item.result().complete(result);
            }
        }
        synchronized (lock) {
            nextAllowedNanos = Math.max(nextAllowedNanos, System.nanoTime() + o.minIntervalMs() * 1_000_000L);
        }
    }

    private boolean isStopping() {
        synchronized (lock) {
            return stopping;
        }
    }

    private void sleep(long millis) throws InterruptedException {
        long until = System.nanoTime() + millis * 1_000_000L;
        synchronized (lock) {
            while (!stopping) {
                long remaining = until - System.nanoTime();
                if (remaining <= 0) {
                    return;
                }
                lock.wait(Math.max(1, remaining / 1_000_000L));
            }
        }
    }

    private HttpRequest request(String target, String body, long timeoutMs) {
        return HttpRequest.newBuilder(URI.create(target)).timeout(Duration.ofMillis(Math.max(1, timeoutMs)))
            .header("Content-Type", "application/json").header("User-Agent", "Moderra-Discord")
            .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8)).build();
    }

    private static long retryAfterMs(HttpResponse<String> response) {
        String header = response.headers().firstValue("Retry-After").orElse("");
        try {
            if (!header.isBlank()) {
                return Math.max(0, Math.round(Double.parseDouble(header.trim()) * 1000));
            }
        } catch (NumberFormatException ignored) {
            // fall through to the body
        }
        Matcher m = RETRY_AFTER_JSON.matcher(response.body() == null ? "" : response.body());
        if (m.find()) {
            return Math.max(0, Math.round(Double.parseDouble(m.group(1)) * 1000));
        }
        return 1000;
    }

    /** {@code X-RateLimit-Remaining: 0} means: wait {@code X-RateLimit-Reset-After} seconds before the next request. */
    private void noteRateLimit(HttpResponse<String> response) {
        if (!"0".equals(response.headers().firstValue("X-RateLimit-Remaining").orElse(""))) {
            return;
        }
        try {
            double seconds = Double.parseDouble(response.headers().firstValue("X-RateLimit-Reset-After").orElse("0"));
            long millis = Math.min(MAX_RATE_WAIT_MS, Math.round(seconds * 1000));
            synchronized (lock) {
                nextAllowedNanos = Math.max(nextAllowedNanos, System.nanoTime() + millis * 1_000_000L);
            }
        } catch (NumberFormatException ignored) {
            // ignore a header we cannot read
        }
    }

    // ------------------------------------------------------------------------------------------
    // Payload
    // ------------------------------------------------------------------------------------------

    /** The JSON of one request for these messages. */
    static String payload(List<Item> batch, String username) {
        Map<String, Object> json = new LinkedHashMap<>();
        if (username != null && !username.isBlank()) {
            json.put("username", truncate(username, 80));
        }
        Set<String> mentions = new LinkedHashSet<>();
        StringBuilder content = new StringBuilder();
        List<Object> embeds = new ArrayList<>();
        for (Item item : batch) {
            if (item.mention() != null && !item.mention().isBlank()) {
                mentions.add(item.mention().trim());
            }
            if (item.embed() != null) {
                embeds.add(item.embed());
            }
            if (item.line() != null) {
                content.append(content.length() == 0 ? "" : "\n").append(item.line());
            }
        }
        String head = String.join(" ", mentions);
        String text = (head.isEmpty() ? "" : head + (content.length() == 0 ? "" : "\n")) + content;
        if (!text.isEmpty()) {
            json.put("content", truncate(text, CONTENT_LIMIT));
        }
        if (!embeds.isEmpty()) {
            json.put("embeds", embeds);
        }
        json.put("allowed_mentions", allowedMentions(head));
        return DataFile.toJson(json);
    }

    /** Only what the admin wrote in {@code discord.mention} can ping anybody. */
    static Map<String, Object> allowedMentions(String mentionText) {
        Map<String, Object> allowed = new LinkedHashMap<>();
        List<String> parse = new ArrayList<>();
        if (mentionText.contains("@everyone") || mentionText.contains("@here")) {
            parse.add("everyone");
        }
        allowed.put("parse", parse);
        List<String> roles = new ArrayList<>();
        List<String> users = new ArrayList<>();
        Matcher m = Pattern.compile("<@(&?)!?(\\d{1,25})>").matcher(mentionText);
        while (m.find()) {
            (m.group(1).isEmpty() ? users : roles).add(m.group(2));
        }
        if (!roles.isEmpty()) {
            allowed.put("roles", roles);
        }
        if (!users.isEmpty()) {
            allowed.put("users", users);
        }
        return allowed;
    }

    static String truncate(String text, int max) {
        return text.length() <= max ? text : text.substring(0, Math.max(0, max - 3)) + "...";
    }

    // ------------------------------------------------------------------------------------------

    private String mask(String text) {
        return Secrets.mask(text, secrets.get());
    }

    private void warn(String message) {
        log.warn(mask(message));
    }
}
