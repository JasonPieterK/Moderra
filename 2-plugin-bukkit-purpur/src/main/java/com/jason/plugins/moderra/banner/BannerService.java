package com.jason.plugins.moderra.banner;

import com.jason.plugins.moderra.engine.Engine;
import com.jason.plugins.moderra.engine.config.CoreSettings;
import com.jason.plugins.moderra.engine.config.WordLists;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Prints the startup and shutdown banners. Data is gathered on the main thread; the animation runs on its own
 * daemon thread and writes to the process's real stdout (file descriptor 1), because {@code System.out} is
 * wrapped by the server logger, which would prefix every line with a timestamp and break in-place redrawing.
 */
public final class BannerService {

    private static final long FRAME_MS = 45;
    private static final long MAX_ANIMATION_NANOS = 2_500_000_000L;
    /** Serialises everything written to the raw console so two banners never interleave. */
    private static final ReentrantLock CONSOLE = new ReentrantLock();

    private final JavaPlugin plugin;
    private final Engine engine;
    private final Supplier<List<BannerData.Hook>> hooks;
    private final Supplier<BannerData.Hook> discord;
    private final long enabledAt;
    private volatile Thread thread;
    private volatile boolean cancelled;

    /**
     * @param enabledAt start of onEnable in epoch millis (startup time and uptime are measured from here)
     * @param hooks     live state of the soft-dependency hooks, read on the main thread
     * @param discord   Discord alert status ({@code name} = text, {@code ok} = alerts are delivered), read on the main thread
     */
    public BannerService(JavaPlugin plugin, Engine engine, long enabledAt,
                         Supplier<List<BannerData.Hook>> hooks, Supplier<BannerData.Hook> discord) {
        this.plugin = plugin;
        this.engine = engine;
        this.enabledAt = enabledAt;
        this.hooks = hooks;
        this.discord = discord;
    }

    // ── Startup ─────────────────────────────────────────────────────────────────────────

    /** Call at the end of onEnable (main thread). */
    public void showStartup() {
        long elapsedMs = System.currentTimeMillis() - enabledAt;
        BannerTerminal.Decision decision = decide();
        if (decision.style() == BannerTerminal.Style.OFF) {
            plugin.getLogger().info(BannerRenderer.plainSummary(gather(elapsedMs)));
            return;
        }
        if (decision.style() == BannerTerminal.Style.FULL_ANIMATED) {
            // Runs on the first server tick, after the "Done" line, so other plugins' startup logs do not
            // land in the middle of the redrawn area. Gathered on the main thread, drawn off-thread.
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (!plugin.isEnabled()) return;
                BannerData data = gather(elapsedMs);
                plugin.getLogger().info(BannerRenderer.plainSummary(data));
                startAnimation(data, decision.color());
            });
            return;
        }
        BannerData data = gather(elapsedMs);
        BannerRenderer r = new BannerRenderer(decision.color(), BannerRenderer.MIN_WIDTH);
        printLines(decision.style() == BannerTerminal.Style.COMPACT ? r.compact(data) : r.fullFinal(data));
        plugin.getLogger().info(BannerRenderer.plainSummary(data));
    }

    // ── Shutdown ────────────────────────────────────────────────────────────────────────

    /** Call at the end of onDisable. */
    public void showShutdown() {
        cancel();
        BannerTerminal.Decision decision = decide();
        long uptime = System.currentTimeMillis() - enabledAt;
        if (decision.style() != BannerTerminal.Style.OFF) {
            printLines(new BannerRenderer(decision.color(), BannerRenderer.MIN_WIDTH).shutdown(version(), uptime));
        }
        plugin.getLogger().info("Moderra v" + version() + " disabled cleanly after " + BannerRenderer.formatUptime(uptime) + ".");
    }

    /** Stops a running animation immediately (restores the cursor). Safe to call repeatedly. */
    public void cancel() {
        cancelled = true;
        Thread t = thread;
        if (t != null) {
            t.interrupt();
            try { t.join(400); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }
    }

    // ── Internals ───────────────────────────────────────────────────────────────────────

    private BannerTerminal.Decision decide() {
        String cfg = engine.value(CoreSettings.STARTUP_BANNER);
        int rows = parseInt(System.getenv("LINES")), cols = parseInt(System.getenv("COLUMNS"));
        return BannerTerminal.decide(cfg, System.getenv(), isTerminal(), rows, cols);
    }

    private static int parseInt(String s) {
        try { return s == null ? 0 : Integer.parseInt(s.trim()); } catch (NumberFormatException e) { return 0; }
    }

    /** True when stdout is a real terminal. On Java 22+ System.console() is non-null even when redirected. */
    static boolean isTerminal() {
        java.io.Console console = System.console();
        if (console == null) return false;
        try {
            Method m = java.io.Console.class.getMethod("isTerminal");
            return (Boolean) m.invoke(console);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return true; // Java 21: a non-null console means an interactive terminal
        }
    }

    @SuppressWarnings("deprecation")
    private String version() {
        return plugin.getDescription().getVersion();
    }

    private BannerData gather(long elapsedMs) {
        String platform = "Bukkit";
        try {
            String name = plugin.getServer().getName();
            if (name != null && !name.isBlank() && !name.equalsIgnoreCase("CraftBukkit")) platform = name;
        } catch (RuntimeException ignored) { }
        String mc = plugin.getServer().getBukkitVersion().split("-")[0];

        List<BannerData.Hook> hookList = new ArrayList<>();
        try {
            hookList.addAll(hooks.get());
        } catch (RuntimeException ignored) { }
        BannerData.Hook disc = new BannerData.Hook("alerts off", false);
        try {
            disc = discord.get();
        } catch (RuntimeException ignored) { }

        int on = 0, total = 0, words = 0, profiles = 0, problems = 0;
        try {
            java.util.Set<String> enabled = new java.util.HashSet<>(engine.settings().enabledLanguages());
            for (String code : engine.wordLists().codes()) {
                if (code.equals(WordLists.CUSTOM)) continue;
                total++;
                if (enabled.contains(code)) on++;
            }
            words = engine.settings().activeWords().size();
            profiles = engine.profileNames().size();
            problems = engine.problems().size();
        } catch (RuntimeException ignored) { }

        return new BannerData(version(), platform, mc, System.getProperty("java.version", "?"),
                on, total, words, profiles, elapsedMs, hookList, problems, disc.name(), disc.ok());
    }

    private void startAnimation(BannerData data, ColorMode color) {
        cancelled = false;
        Thread t = new Thread(() -> animate(data, color), "Moderra-Banner");
        t.setDaemon(true);
        thread = t;
        t.start();
    }

    private void animate(BannerData data, ColorMode color) {
        BannerRenderer r = new BannerRenderer(color, BannerRenderer.MIN_WIDTH);
        CONSOLE.lock();
        PrintStream out = null;
        boolean cursorHidden = false;
        try {
            out = rawOut();
            long deadline = System.nanoTime() + MAX_ANIMATION_NANOS;
            out.print("\u001b[?25l");
            cursorHidden = true;
            int drawn = 0;
            for (int f = 0; f <= r.finalFrame(); f++) {
                if (cancelled || !plugin.isEnabled()) break;
                if (System.nanoTime() > deadline) f = r.finalFrame(); // running late: jump to the final frame
                writeFrame(out, r.full(data, f), drawn > 0 ? BannerRenderer.FULL_HEIGHT : 0);
                drawn++;
                if (f == r.finalFrame()) break;
                Thread.sleep(FRAME_MS);
            }
        } catch (InterruptedException ignored) {
            // cancelled by onDisable
        } catch (Throwable t) {
            plugin.getLogger().fine("Banner animation stopped: " + t);
        } finally {
            if (out != null && cursorHidden) { out.print("\u001b[?25h"); out.flush(); }
            thread = null;
            CONSOLE.unlock();
        }
    }

    private static void writeFrame(PrintStream out, List<String> lines, int moveUp) {
        StringBuilder sb = new StringBuilder(lines.size() * 140);
        if (moveUp > 0) sb.append("\u001b[").append(moveUp).append("A\r");
        for (String line : lines) sb.append(line).append('\n');
        out.print(sb);
        out.flush();
    }

    private void printLines(List<String> lines) {
        try {
            if (!CONSOLE.tryLock(500, java.util.concurrent.TimeUnit.MILLISECONDS)) return;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }
        try {
            PrintStream out = rawOut();
            writeFrame(out, lines, 0);
        } catch (Throwable t) {
            plugin.getLogger().fine("Banner print failed: " + t);
        } finally {
            CONSOLE.unlock();
        }
    }

    private static PrintStream rawOut() {
        return new PrintStream(new FileOutputStream(FileDescriptor.out), true, StandardCharsets.UTF_8);
    }

}
