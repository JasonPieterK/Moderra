package com.jason.plugins.moderra.engine.command;

import static com.jason.plugins.moderra.engine.command.Chat.GOLD;
import static com.jason.plugins.moderra.engine.command.Chat.GRAY;
import static com.jason.plugins.moderra.engine.command.Chat.GREEN;
import static com.jason.plugins.moderra.engine.command.Chat.RED;
import static com.jason.plugins.moderra.engine.command.Chat.WHITE;
import static com.jason.plugins.moderra.engine.command.Chat.YELLOW;

import com.jason.plugins.moderra.engine.Engine;
import com.jason.plugins.moderra.engine.PlayerRef;
import com.jason.plugins.moderra.engine.moderation.HistorySettings;
import com.jason.plugins.moderra.engine.moderation.HistoryStore;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * {@code /moderra history <player> [page]} (newest first), {@code /moderra history recent [n]} and
 * {@code /moderra history purge <player> [confirm]} (admins only, asks twice). Needs {@code moderra.history}.
 * Plain text, no clickable parts.
 */
public final class HistoryCommand implements SubCommand {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.ROOT).withZone(ZoneOffset.UTC);

    @Override
    public String name() {
        return "history";
    }

    @Override
    public String permission() {
        return HistoryStore.PERMISSION;
    }

    @Override
    public void execute(CommandContext c) {
        if (c.args().length == 0) {
            c.usage("<player> [page] | recent [n] | purge <player> [confirm]");
            return;
        }
        String first = c.args()[0].toLowerCase(Locale.ROOT);
        if (first.equals("recent")) {
            recent(c);
        } else if (first.equals("purge")) {
            purge(c);
        } else {
            player(c);
        }
    }

    private static int pageSize(Engine engine) {
        return engine.value(HistorySettings.PAGE_SIZE);
    }

    private static void player(CommandContext c) {
        int page = 1;
        if (c.args().length >= 2) {
            try {
                page = Integer.parseInt(c.args()[1]);
            } catch (NumberFormatException ex) {
                c.usage("<player> [page]");
                return;
            }
        }
        Optional<PlayerRef> ref = c.engine().resolvePlayer(c.args()[0]);
        if (ref.isEmpty()) {
            c.send(Chat.error("Player not found (not online and never seen on this server)."));
            return;
        }
        int size = pageSize(c.engine());
        HistoryStore.Page first = c.engine().history().find(ref.get().id(), 1, size);
        if (first.total() == 0) {
            c.send(Chat.warn("No history exists for " + Chat.val(ref.get().name()) + "."));
            return;
        }
        int pages = (first.total() + size - 1) / size;
        page = Math.max(1, Math.min(pages, page));
        HistoryStore.Page shown = page == 1 ? first : c.engine().history().find(ref.get().id(), page, size);
        c.send(Chat.heading("History of " + ref.get().name()) + GRAY + " (" + WHITE + first.total() + GRAY + " entries, newest first, UTC), page " + WHITE + page + GRAY + " of " + WHITE + pages + GRAY + ":");
        for (HistoryStore.Entry e : shown.entries()) {
            c.send(line(e, false));
        }
        if (page < pages) {
            c.send(Chat.hint("Next page: /" + c.label() + " history " + ref.get().name() + " " + (page + 1)));
        }
    }

    private static void recent(CommandContext c) {
        int n = pageSize(c.engine());
        if (c.args().length >= 2) {
            try {
                n = Integer.parseInt(c.args()[1]);
            } catch (NumberFormatException ex) {
                c.usage("recent [n]");
                return;
            }
        }
        n = Math.max(1, Math.min(100, n));
        List<HistoryStore.Entry> entries = c.engine().history().recent(n);
        if (entries.isEmpty()) {
            c.send(Chat.warn("The history is empty."));
            return;
        }
        c.send(Chat.heading("Newest entries") + GRAY + " (" + WHITE + entries.size() + GRAY + ", UTC):");
        for (HistoryStore.Entry e : entries) {
            c.send(line(e, true));
        }
    }

    private static void purge(CommandContext c) {
        if (!c.sender().has(Engine.PERMISSION_ADMIN)) {
            c.send(Chat.error("Only admins (moderra.admin) can delete history."));
            return;
        }
        boolean confirmed = c.args().length >= 3 && c.args()[2].equalsIgnoreCase("confirm");
        if (c.args().length < 2 || (c.args().length >= 3 && !confirmed) || c.args().length > 3) {
            c.usage("purge <player> [confirm]");
            return;
        }
        Optional<PlayerRef> ref = c.engine().resolvePlayer(c.args()[1]);
        if (ref.isEmpty()) {
            c.send(Chat.error("Player not found (not online and never seen on this server)."));
            return;
        }
        HistoryStore history = c.engine().history();
        if (!confirmed) {
            int count = history.count(ref.get().id());
            if (count == 0) {
                c.send(Chat.warn("No history exists for " + Chat.val(ref.get().name()) + "."));
            } else {
                c.send(Chat.warn("This action permanently deletes " + Chat.val(count) + " history entries of " + Chat.val(ref.get().name()) + ". To confirm, enter: " + WHITE + "/"
                    + c.label() + " history purge " + ref.get().name() + " confirm"));
            }
            return;
        }
        int removed = history.purge(ref.get().id());
        c.send(Chat.ok("Deleted " + Chat.val(removed) + " history entries of " + Chat.val(ref.get().name()) + "."));
        c.audit();
    }

    /** One plain line; text from players is stripped of colour codes and control characters. */
    static String line(HistoryStore.Entry e, boolean withPlayer) {
        StringBuilder sb = new StringBuilder(GRAY).append(TIME.format(Instant.ofEpochMilli(e.time()))).append(' ').append(YELLOW)
            .append(clean(e.kind()));
        if (withPlayer && !e.player().isEmpty()) {
            sb.append(' ').append(WHITE).append(clean(e.player()));
        }
        List<String> parts = new ArrayList<>();
        if (!e.source().isEmpty()) {
            parts.add(e.source());
        }
        if (!e.check().isEmpty()) {
            parts.add(e.check());
        }
        if (!parts.isEmpty()) {
            sb.append(' ').append(GRAY).append(clean(String.join("/", parts)));
        }
        if (!e.detail().isEmpty()) {
            sb.append(' ').append(WHITE).append(clean(e.detail()));
        }
        List<String> facts = new ArrayList<>();
        if (e.points() >= 0) {
            facts.add(e.points() + " strikes");
        }
        if (!e.step().isEmpty()) {
            facts.add("step " + e.step());
        }
        if (!e.actor().isEmpty() && !e.actor().equals("system")) {
            facts.add("by " + e.actor());
        }
        if (!facts.isEmpty()) {
            sb.append(' ').append(GRAY).append('(').append(clean(String.join(", ", facts))).append(')');
        }
        if (e.text() != null) {
            sb.append(' ').append(GRAY).append('"').append(clean(e.text())).append('"');
        }
        return sb.toString();
    }

    private static String clean(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch != '§' && ch >= 0x20 && ch != 0x7f) {
                sb.append(ch);
            }
        }
        return sb.toString();
    }

    @Override
    public List<String> complete(CommandContext c) {
        String[] a = c.args();
        if (a.length == 1) {
            List<String> out = new ArrayList<>(CommandRegistry.prefix(List.of("recent", "purge"), a[0]));
            out.addAll(c.engine().players().names(a[0], 50));
            return out;
        }
        if (a.length == 2 && a[0].equalsIgnoreCase("purge")) {
            return c.engine().players().names(a[1], 50);
        }
        if (a.length == 3 && a[0].equalsIgnoreCase("purge")) {
            return CommandRegistry.prefix(List.of("confirm"), a[2]);
        }
        return List.of();
    }
}
