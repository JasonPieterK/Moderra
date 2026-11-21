package com.jason.plugins.moderra.banner;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Writes {@code banner-preview.ans} (truecolor ANSI) and {@code banner-preview.txt} (plain) of the final frame with sample data.
 * Run: {@code java -cp target/classes:target/test-classes com.jason.plugins.moderra.banner.BannerPreview <folder>}.
 */
public final class BannerPreview {
    private BannerPreview() {}

    public static void main(String[] args) throws IOException {
        Path dir = Path.of(args.length > 0 ? args[0] : ".");
        BannerData d = new BannerData("1.0", "Paper", "1.21.11", "21.0.5", 3, 40, 1234, 2, 812,
            List.of(new BannerData.Hook("EssentialsX", true), new BannerData.Hook("DiscordSRV", false),
                new BannerData.Hook("PlaceholderAPI", true), new BannerData.Hook("LuckPerms", true),
                new BannerData.Hook("Vault", false), new BannerData.Hook("ProtocolLib", false),
                new BannerData.Hook("CoreProtect", true), new BannerData.Hook("Plan", false)),
            0, "webhooks on", true);
        Files.writeString(dir.resolve("banner-preview.ans"),
            String.join("\n", new BannerRenderer(ColorMode.TRUECOLOR, 72).fullFinal(d)) + "\n", StandardCharsets.UTF_8);
        Files.writeString(dir.resolve("banner-preview.txt"),
            String.join("\n", new BannerRenderer(ColorMode.NONE, 72).fullFinal(d)) + "\n", StandardCharsets.UTF_8);
    }
}
