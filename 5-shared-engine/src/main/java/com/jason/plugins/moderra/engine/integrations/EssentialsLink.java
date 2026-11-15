package com.jason.plugins.moderra.engine.integrations;

import com.jason.plugins.moderra.engine.Engine;
import com.jason.plugins.moderra.engine.ModerationEvent;
import com.jason.plugins.moderra.engine.Verdict;
import com.jason.plugins.moderra.mod.Names;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * The engine side of the EssentialsX hook. The Bukkit plugin attaches an {@link EssentialsBridge} when EssentialsX is
 * installed ({@code engine.essentials().attach(bridge)}); until then (and on Fabric and Forge) every method here is a harmless
 * no-op that answers "nothing special". All failures of the bridge are caught: Essentials can never break a check.
 */
public final class EssentialsLink {
    private static final Pattern COLOUR_CODES = Pattern.compile("[§&][0-9a-fk-orA-FK-OR]");
    private static final String NOTIFY = "moderra.notify";

    private final Engine engine;
    private volatile EssentialsBridge bridge;
    private volatile boolean warnedMode;

    public EssentialsLink(Engine engine) {
        this.engine = engine;
    }

    /** Registers the reload check. Called once by the engine. */
    public void install() {
        engine.addReloadListener(this::checkSettings);
    }

    /** Called by the Bukkit plugin when EssentialsX is found and usable. */
    public void attach(EssentialsBridge essentialsBridge) {
        this.bridge = essentialsBridge;
        warnedMode = false;
    }

    public void detach() {
        this.bridge = null;
    }

    /** Whether EssentialsX is connected and the hook is switched on. */
    public boolean active() {
        return bridge != null && engine.value(EssentialsSettings.ESSENTIALS);
    }

    private void checkSettings() {
        if (!warnedMode && bridge == null && !EssentialsSettings.MUTE_OWN.equals(engine.value(EssentialsSettings.MUTE_MODE))) {
            warnedMode = true;
            engine.platform().logger().warn("integrations.essentials.mute-mode is '" + engine.value(EssentialsSettings.MUTE_MODE)
                + "' but EssentialsX is not connected: the built-in mute is used.");
        }
    }

    // ------------------------------------------------------------------------------------------
    // Mute
    // ------------------------------------------------------------------------------------------

    /** The mute mode in effect: {@code own} unless Essentials is connected. */
    public String muteMode() {
        return active() ? engine.value(EssentialsSettings.MUTE_MODE) : EssentialsSettings.MUTE_OWN;
    }

    /** Whether mutes are written to Essentials ({@code essentials} or {@code both}). */
    public boolean writesEssentialsMute() {
        return !EssentialsSettings.MUTE_OWN.equals(muteMode());
    }

    /** Whether the built-in mute list is also kept ({@code own} or {@code both}). */
    public boolean keepsOwnMute() {
        return !EssentialsSettings.MUTE_ESSENTIALS.equals(muteMode());
    }

    /** The Essentials mute of a player; only asked in modes {@code essentials} and {@code both}. */
    public Optional<EssentialsBridge.ExternalMute> externalMute(UUID player) {
        if (!writesEssentialsMute()) {
            return Optional.empty();
        }
        try {
            return bridge.mute(player);
        } catch (RuntimeException | LinkageError ex) {
            fail("reading a mute", ex);
            return Optional.empty();
        }
    }

    /** Mutes in Essentials. @param until end in ms since 1970 or -1 for permanent. @return whether Essentials did it */
    public boolean setExternalMute(UUID player, String name, long until, String reason) {
        if (!writesEssentialsMute()) {
            return false;
        }
        try {
            return bridge.setMute(player, name, until, reason == null ? "" : reason);
        } catch (RuntimeException | LinkageError ex) {
            fail("muting " + name, ex);
            return false;
        }
    }

    /** Lifts an Essentials mute. */
    public boolean clearExternalMute(UUID player) {
        if (!writesEssentialsMute()) {
            return false;
        }
        try {
            return bridge.clearMute(player);
        } catch (RuntimeException | LinkageError ex) {
            fail("unmuting", ex);
            return false;
        }
    }

    // ------------------------------------------------------------------------------------------
    // Nicknames
    // ------------------------------------------------------------------------------------------

    /** The name to show in broadcasts and staff notices, according to {@code nickname-in-messages}. */
    public String displayName(UUID player, String realName) {
        String how = engine.value(EssentialsSettings.NICKNAME_IN_MESSAGES);
        if (!active() || player == null || EssentialsSettings.NICK_OFF.equals(how)) {
            return realName;
        }
        String nick = plainNickname(player);
        if (nick.isEmpty() || nick.equalsIgnoreCase(realName)) {
            return realName;
        }
        return EssentialsSettings.NICK_BOTH.equals(how) ? nick + " (" + realName + ")" : nick;
    }

    private String plainNickname(UUID player) {
        try {
            String nick = bridge.nickname(player);
            return nick == null ? "" : COLOUR_CODES.matcher(nick).replaceAll("").strip();
        } catch (RuntimeException | LinkageError ex) {
            fail("reading a nickname", ex);
            return "";
        }
    }

    /** Checks the nickname of a player who just joined (see {@code check-nickname-on-join}). Main thread. */
    public void onJoin(UUID player, String name) {
        if (!active() || !engine.value(EssentialsSettings.CHECK_NICKNAME)) {
            return;
        }
        String nick = plainNickname(player);
        if (nick.isEmpty()) {
            return;
        }
        Verdict verdict = engine.inspectName(player, name, nick);
        if (!(verdict.blocked() || verdict.censored()) || verdict.primary() == null) {
            return;
        }
        String action = engine.value(EssentialsSettings.NICKNAME_ACTION);
        String word = verdict.primary().detail() == null ? "" : verdict.primary().detail();
        engine.platform().logger().info(name + " has a nickname that is not allowed (" + verdict.primary().check() + ": '" + word
            + "'), action: " + action);
        Map<String, String> data = new HashMap<>();
        data.put("check", verdict.primary().check());
        data.put("detail", word);
        data.put("action", action);
        data.put("weight", "0");
        data.put("text", nick);
        engine.events().publish(new ModerationEvent(ModerationEvent.VIOLATION, engine.now(), player, name, engine.platform().worldName(player),
            "nick", data));
        if (!EssentialsSettings.NICK_LOG.equals(action)) {
            String text = engine.value(EssentialsSettings.NICKNAME_MESSAGE);
            if (text != null && !text.isBlank()) {
                engine.platform().sendMessage(player, Names.colour(text));
            }
        }
        if (EssentialsSettings.NICK_RESET.equals(action)) {
            try {
                if (!bridge.resetNickname(player)) {
                    engine.platform().logger().warn("The nickname of " + name + " could not be removed.");
                }
            } catch (RuntimeException | LinkageError ex) {
                fail("removing a nickname", ex);
            }
        }
    }

    // ------------------------------------------------------------------------------------------
    // Vanish
    // ------------------------------------------------------------------------------------------

    /** Whether lines about this player must be hidden from normal players (vanished and {@code hide-vanished} on). */
    public boolean vanished(UUID player) {
        if (!active() || player == null || !engine.value(EssentialsSettings.HIDE_VANISHED)) {
            return false;
        }
        try {
            return bridge.vanished(player);
        } catch (RuntimeException | LinkageError ex) {
            fail("reading vanish", ex);
            return false;
        }
    }

    /**
     * Sends a line about a player to everybody, except that a vanished player's line goes only to the staff with
     * {@code moderra.notify} (when {@code hide-vanished} is on).
     */
    public void broadcastAbout(UUID player, String text) {
        if (!vanished(player)) {
            engine.platform().broadcast(text);
            return;
        }
        for (var other : engine.platform().onlinePlayers()) {
            if (engine.platform().has(other.id(), NOTIFY)) {
                engine.platform().sendMessage(other.id(), text);
            }
        }
    }

    // ------------------------------------------------------------------------------------------
    // Extra scanned commands
    // ------------------------------------------------------------------------------------------

    /**
     * For an Essentials command line that the normal settings do not scan: the text after the label, or {@code null}.
     * Only while Essentials is connected; {@code commands.ignored-commands} still wins.
     */
    public String extraCommandText(Set<String> ignoredCommands, String commandLine) {
        if (!active()) {
            return null;
        }
        int space = commandLine.indexOf(' ');
        if (space < 0) {
            return null;
        }
        String label = Names.commandLabel(commandLine.substring(0, space));
        if (ignoredCommands.contains(label)) {
            return null;
        }
        Set<String> labels = new HashSet<>();
        for (String raw : engine.value(EssentialsSettings.SCAN_COMMANDS)) {
            labels.add(raw.strip().toLowerCase(Locale.ROOT));
        }
        return labels.contains(label) ? commandLine.substring(space + 1) : null;
    }

    private void fail(String what, Throwable ex) {
        engine.platform().logger().warn("EssentialsX call failed while " + what + " (" + ex + "); continuing without it.");
    }
}
