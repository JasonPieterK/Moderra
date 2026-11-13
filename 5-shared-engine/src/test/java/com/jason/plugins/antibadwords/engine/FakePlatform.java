package com.jason.plugins.antibadwords.engine;

import com.jason.plugins.antibadwords.PunishmentAction;
import com.jason.plugins.antibadwords.mod.Names;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** A pretend server for the unit tests: remembers everything the engine asked it to do. */
public final class FakePlatform implements Platform {
    public final Path folder;
    public final Map<UUID, String> players = new LinkedHashMap<>();
    public final Map<UUID, Set<String>> permissions = new HashMap<>();
    public final Map<UUID, String> worlds = new HashMap<>();
    public final Map<UUID, String> dimensions = new HashMap<>();
    public final Map<UUID, List<String>> sent = new HashMap<>();
    public final List<String> broadcasts = new ArrayList<>();
    public final List<String> consoleCommands = new ArrayList<>();
    public final List<String> kicks = new ArrayList<>();
    public final List<String> appliedActions = new ArrayList<>();
    public final List<String> infos = new ArrayList<>();
    public final List<String> warnings = new ArrayList<>();
    /** Sources where this pretend server can deliver a changed text (a real Bukkit server: chat). */
    public final Set<String> rewritable = new HashSet<>();

    public FakePlatform(Path folder) {
        this.folder = folder;
    }

    public UUID join(String name, String... nodes) {
        UUID id = UUID.nameUUIDFromBytes(name.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        players.put(id, name);
        permissions.put(id, new HashSet<>(List.of(nodes)));
        worlds.put(id, "world");
        return id;
    }

    public List<String> sentTo(UUID id) {
        return sent.computeIfAbsent(id, k -> new ArrayList<>());
    }

    @Override
    public String name() {
        return "Fake";
    }

    @Override
    public String pluginVersion() {
        return "1";
    }

    @Override
    public Path dataFolder() {
        return folder;
    }

    @Override
    public EngineLog logger() {
        return new EngineLog() {
            @Override
            public void info(String message) {
                infos.add(message);
            }

            @Override
            public void warn(String message) {
                warnings.add(message);
            }
        };
    }

    @Override
    public Collection<PlayerRef> onlinePlayers() {
        return players.entrySet().stream().map(e -> new PlayerRef(e.getKey(), e.getValue())).toList();
    }

    @Override
    public Optional<PlayerRef> findPlayer(String exactName) {
        return onlinePlayers().stream().filter(p -> p.name().equals(exactName)).findFirst();
    }

    @Override
    public void sendMessage(UUID player, String message) {
        sentTo(player).add(message);
    }

    @Override
    public void broadcast(String message) {
        broadcasts.add(message);
    }

    @Override
    public void runConsoleCommand(String command) {
        consoleCommands.add(command);
    }

    @Override
    public void kick(UUID player, String reason) {
        kicks.add(players.get(player) + ": " + reason);
    }

    @Override
    public boolean has(UUID player, String node) {
        return permissions.getOrDefault(player, Set.of()).contains(node);
    }

    @Override
    public String worldName(UUID player) {
        return worlds.getOrDefault(player, "");
    }

    @Override
    public String dimensionName(UUID player) {
        return dimensions.getOrDefault(player, "");
    }

    @Override
    public boolean canRewrite(String source) {
        return rewritable.contains(source);
    }

    @Override
    public void runSync(Runnable task) {
        task.run();
    }

    @Override
    public void runSyncLater(Runnable task, long delayMillis) {
        task.run();
    }

    @Override
    public void runAsync(Runnable task) {
        task.run();
    }

    @Override
    public String applyAction(UUID player, PunishmentAction action) {
        appliedActions.add(action.type() + ":" + action.id());
        return switch (action.type()) {
            case "sound" -> Names.soundDescription(action.args().get(0));
            case "boom" -> throw new IllegalStateException("boom");
            default -> Names.prettyName(action.args().get(0));
        };
    }
}
