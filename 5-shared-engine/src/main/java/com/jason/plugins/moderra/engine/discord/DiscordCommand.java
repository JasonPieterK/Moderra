package com.jason.plugins.moderra.engine.discord;

import com.jason.plugins.moderra.engine.command.Chat;
import com.jason.plugins.moderra.engine.command.CommandContext;
import com.jason.plugins.moderra.engine.command.CommandRegistry;
import com.jason.plugins.moderra.engine.command.SubCommand;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/** {@code /moderra discord test} sends a test message to every configured webhook; {@code /moderra discord status} shows the queue. */
final class DiscordCommand implements SubCommand {
    private static final List<String> WORDS = List.of("test", "status");
    private static final long GIVE_UP_SECONDS = 60;

    private final DiscordService service;

    DiscordCommand(DiscordService service) {
        this.service = service;
    }

    @Override
    public String name() {
        return "discord";
    }

    @Override
    public String permission() {
        return DiscordSettings.PERMISSION;
    }

    @Override
    public List<String> complete(CommandContext c) {
        return c.args().length == 1 ? CommandRegistry.prefix(WORDS, c.args()[0]) : List.of();
    }

    @Override
    public void execute(CommandContext c) {
        String word = c.args().length == 0 ? "" : c.args()[0].toLowerCase(Locale.ROOT);
        switch (word) {
            case "test" -> test(c);
            case "status" -> service.status().forEach(line -> c.send(Chat.GRAY + line));
            default -> c.usage("<test|status>");
        }
    }

    private void test(CommandContext c) {
        if (!service.active()) {
            c.send(Chat.warn("Note: Discord alerts are switched off in discord.yml (enabled / mode); sending the test anyway."));
        }
        Map<String, CompletableFuture<String>> results = service.sendTest(c.sender().name());
        if (results.isEmpty()) {
            c.send(Chat.error("No usable webhook URL is set (discord.webhook.alerts-url / log-url). Use https://... or ${ENV:NAME}."));
            return;
        }
        c.send(Chat.info("The plugin sends the test message to: " + Chat.val(String.join(", ", results.keySet())) + "."));
        // the answer arrives from the sender thread; hand it back to the server thread before talking to the player
        results.forEach((target, result) -> result.orTimeout(GIVE_UP_SECONDS, TimeUnit.SECONDS)
            .exceptionally(ex -> "No answer from Discord.")
            .thenAccept(text -> c.engine().platform().runSync(() -> {
                boolean ok = text.startsWith("OK");
                c.send(ok ? Chat.ok("Discord " + Chat.val(target) + ": " + service.mask(text)) : Chat.error("Discord " + Chat.val(target) + ": " + service.mask(text)));
            })));
    }
}
