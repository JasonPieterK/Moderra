package com.jason.plugins.moderra.banner;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

class BannerRendererTest {

    private static final Pattern ANSI = Pattern.compile("\u001b\\[[0-9;?]*[A-Za-z]");

    private static BannerData data() {
        return new BannerData("1.0", "Paper", "1.21.11", "21.0.5", 3, 40, 1234, 2, 812,
                List.of(new BannerData.Hook("EssentialsX", true), new BannerData.Hook("DiscordSRV", false),
                        new BannerData.Hook("PlaceholderAPI", true), new BannerData.Hook("LuckPerms", true),
                        new BannerData.Hook("Vault", false), new BannerData.Hook("ProtocolLib", false),
                        new BannerData.Hook("CoreProtect", true), new BannerData.Hook("Plan", false)),
                2, "webhooks on", true);
    }

    private static int visible(String line) { return ANSI.matcher(line).replaceAll("").length(); }

    @Test
    void fullBannerIsTallAndEveryLineHasTheSameWidth() {
        for (ColorMode m : ColorMode.values()) {
            BannerRenderer r = new BannerRenderer(m, 72);
            for (int f = 0; f < r.frameCount(); f++) {
                List<String> lines = r.full(data(), f);
                assertTrue(lines.size() >= 20, "frame " + f);
                assertEquals(BannerRenderer.FULL_HEIGHT, lines.size());
                for (String l : lines) assertEquals(72, visible(l), "mode " + m + " frame " + f + ": " + l);
            }
        }
    }

    @Test
    void noAnsiWhenColourDisabled() {
        BannerRenderer r = new BannerRenderer(ColorMode.NONE, 72);
        for (String l : r.fullFinal(data())) assertFalse(l.contains("\u001b"));
        for (String l : r.compact(data())) assertFalse(l.contains("\u001b"));
        for (String l : r.shutdown("1.0", 5000)) assertFalse(l.contains("\u001b"));
        assertTrue(new BannerRenderer(ColorMode.TRUECOLOR, 72).fullFinal(data()).get(3).contains("\u001b[0;1;38;2;"));
        assertTrue(new BannerRenderer(ColorMode.ANSI16, 72).fullFinal(data()).get(3).contains("\u001b["));
    }

    @Test
    void compactAndShutdownSizes() {
        BannerRenderer r = new BannerRenderer(ColorMode.ANSI256, 72);
        List<String> compact = r.compact(data());
        assertEquals(8, compact.size());
        List<String> down = r.shutdown("1.0", 3_700_000);
        assertEquals(6, down.size());
        for (String l : compact) assertEquals(72, visible(l));
        for (String l : down) assertEquals(72, visible(l));
    }

    @Test
    void animationChangesFramesAndEndsStatic() {
        BannerRenderer r = new BannerRenderer(ColorMode.TRUECOLOR, 72);
        assertNotEquals(r.full(data(), 0), r.full(data(), 5));
        assertNotEquals(r.full(data(), 20), r.full(data(), 21));
        assertEquals(r.fullFinal(data()), r.full(data(), r.finalFrame() + 50));
        assertEquals(r.full(data(), r.finalFrame()), r.full(data(), r.finalFrame()));
    }

    @Test
    void finalFrameContainsTheFacts() {
        String all = String.join("\n", new BannerRenderer(ColorMode.NONE, 72).fullFinal(data()));
        for (String s : new String[]{"v1.0", "Paper", "1.21.11", "21.0.5", "EssentialsX", "PlaceholderAPI", "webhooks on",
                "run /moderra", "812 ms", "1234", "3 / 40", "[ VERSION ]", "[ LOADED ]", "[ DISCORD ]", "[ CHAT MODERATION ]",
                "✔", "✖", "2 config problem(s)"}) {
            assertTrue(all.contains(s), "missing " + s);
        }
        assertFalse(all.toLowerCase().contains("jason"), "no author credit");
    }

    @Test
    void logoSpellsModerraInBlockLetters() {
        // 6 rows of logo (frame rows 2..7), 7 letters x 5 columns + 6 gaps of 2
        List<String> lines = new BannerRenderer(ColorMode.NONE, 72).fullFinal(data());
        int blocks = 0;
        for (int y = 2; y <= 7; y++) for (char ch : lines.get(y).toCharArray()) if (ch == '█') blocks++;
        assertTrue(blocks > 60, "logo blocks: " + blocks);
    }

    @Test
    void widerConsoleCentresAndStillAligns() {
        BannerRenderer r = new BannerRenderer(ColorMode.TRUECOLOR, 100);
        for (String l : r.fullFinal(data())) assertEquals(100, visible(l));
        assertEquals(72, new BannerRenderer(ColorMode.NONE, 10).width());
    }

    @Test
    void untrustedTextCannotInjectEscapes() {
        BannerData evil = new BannerData("1\u001b[31m", "Pa\u001bper", "26.2", "25", 1, 1, 1, 1, 1,
                List.of(new BannerData.Hook("x\u001by", true)), 0, "http\u001b[2J", true);
        for (String l : new BannerRenderer(ColorMode.NONE, 72).fullFinal(evil)) assertFalse(l.contains("\u001b"));
        for (String l : new BannerRenderer(ColorMode.NONE, 72).compact(evil)) assertFalse(l.contains("\u001b"));
        for (String l : new BannerRenderer(ColorMode.NONE, 72).shutdown("1\u001b[2J", 1)) assertFalse(l.contains("\u001b"));
        assertFalse(BannerRenderer.plainSummary(evil).contains("\u001b"));
        assertEquals("a?b?c", BannerRenderer.clean("a\u001bb\nc"));
    }

    @Test
    void plainSummaryIsOneLineWithoutColour() {
        String s = BannerRenderer.plainSummary(data());
        assertFalse(s.contains("\n"));
        assertFalse(s.contains("\u001b"));
        assertTrue(s.contains("3/40 language(s)") && s.contains("1234 blocked word(s)") && s.contains("EssentialsX on")
                && s.contains("Vault off") && s.contains("discord: webhooks on") && s.contains("2 config problem(s)"), s);
    }

    @Test
    void colourFallbacks() {
        assertEquals("38;2;255;230;0", Canvas.code(ColorMode.TRUECOLOR, Tone.YELLOW, false));
        assertEquals("103", Canvas.code(ColorMode.ANSI16, Tone.YELLOW, true));
        assertEquals("92", Canvas.code(ColorMode.ANSI16, Tone.LIME, false));
        assertEquals("40", Canvas.code(ColorMode.ANSI16, Tone.BLACK, true));
        assertEquals(16, Canvas.to256(0x000000));
        assertEquals(231, Canvas.to256(0xFFFFFF));
    }

    @Test
    void uptimeFormatting() {
        assertEquals("5s", BannerRenderer.formatUptime(5_000));
        assertEquals("1h 1m", BannerRenderer.formatUptime(3_700_000));
    }
}
