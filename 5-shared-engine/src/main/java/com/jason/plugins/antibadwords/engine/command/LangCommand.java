package com.jason.plugins.antibadwords.engine.command;

import static com.jason.plugins.antibadwords.engine.command.Chat.GOLD;
import static com.jason.plugins.antibadwords.engine.command.Chat.GRAY;
import static com.jason.plugins.antibadwords.engine.command.Chat.WHITE;
import static com.jason.plugins.antibadwords.engine.command.Chat.GREEN;
import static com.jason.plugins.antibadwords.engine.command.Chat.RED;
import static com.jason.plugins.antibadwords.engine.command.Chat.YELLOW;

import com.jason.plugins.antibadwords.engine.Engine;
import com.jason.plugins.antibadwords.engine.config.CoreSettings;
import com.jason.plugins.antibadwords.engine.config.WordList;
import com.jason.plugins.antibadwords.engine.config.WordLists;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * {@code /abw lang list | enable <code> | disable <code>}: switches the language word lists on and off. The change is
 * written to {@code filters.enabled-word-lists} in config.yml (comments stay) and works at once.
 */
public final class LangCommand implements SubCommand {
    @Override
    public String name() {
        return "lang";
    }

    @Override
    public void execute(CommandContext c) {
        String action = c.args().length == 0 ? "list" : c.args()[0].toLowerCase(java.util.Locale.ROOT);
        switch (action) {
            case "list" -> list(c);
            case "enable", "disable" -> {
                if (c.args().length < 2) {
                    c.usage(action + " <language code>");
                    return;
                }
                change(c, action.equals("enable"), WordLists.canonicalCode(c.args()[1]));
            }
            default -> c.usage("<list|enable|disable> [language code]");
        }
    }

    /** The codes that are on in the files (old names such as "english" are read as their new code). */
    private static List<String> enabled(Engine engine) {
        List<String> out = new ArrayList<>();
        engine.value(CoreSettings.ENABLED_LANGUAGES).forEach(name -> out.add(WordLists.canonicalCode(name)));
        return out;
    }

    private static void list(CommandContext c) {
        Engine engine = c.engine();
        List<String> on = enabled(engine);
        List<String> parts = new ArrayList<>();
        int count = 0;
        for (String code : engine.wordLists().codes()) {
            boolean active = code.equals(WordLists.CUSTOM) || on.contains(code);
            count += active ? 1 : 0;
            parts.add((active ? GREEN : GRAY) + code);
        }
        c.send(Chat.heading("Languages") + GRAY + " (" + WHITE + count + GRAY + " of " + WHITE + parts.size() + GRAY + " on; green = on):");
        c.send(String.join(GRAY + ", ", parts));
        c.send(Chat.hint("Use /" + c.label() + " lang enable <code> or /" + c.label() + " lang disable <code> to change a language."));
    }

    private static void change(CommandContext c, boolean enable, String code) {
        Engine engine = c.engine();
        if (code.equals(WordLists.CUSTOM)) {
            c.send(Chat.warn("The custom list is always on."));
            return;
        }
        WordList list = engine.wordLists().get(code);
        boolean isOn = enabled(engine).contains(code);
        try {
            if (enable) {
                if (list == null) {
                    c.send(Chat.error("This language is not known: " + Chat.val(code) + ". Use /" + c.label() + " lang list."));
                    return;
                }
                if (isOn) {
                    c.send(Chat.warn("The language " + Chat.val(code) + " is already on."));
                    return;
                }
                engine.addToList(CoreSettings.ENABLED_LANGUAGES, code);
                c.send(Chat.ok("The language " + Chat.val(code) + " (" + list.name() + ") is on. Blocked words: " + Chat.val(engine.settings().matcher().size()) + "."));
            } else {
                if (!isOn) {
                    c.send(Chat.warn("The language " + Chat.val(code) + " is not on."));
                    return;
                }
                engine.removeFromList(CoreSettings.ENABLED_LANGUAGES, name -> WordLists.canonicalCode(name).equals(code));
                c.send(Chat.ok("The language " + Chat.val(code) + " is off. Blocked words: " + Chat.val(engine.settings().matcher().size()) + "."));
            }
            c.audit();
        } catch (IOException | RuntimeException ex) {
            c.send(Chat.error("The plugin cannot save config.yml: " + ex.getMessage()));
        }
    }

    @Override
    public List<String> complete(CommandContext c) {
        if (c.args().length == 1) {
            return CommandRegistry.prefix(List.of("list", "enable", "disable"), c.args()[0]);
        }
        if (c.args().length == 2) {
            List<String> on = enabled(c.engine());
            List<String> codes = new ArrayList<>(c.engine().wordLists().codes());
            codes.remove(WordLists.CUSTOM);
            if (c.args()[0].equalsIgnoreCase("enable")) {
                codes.removeAll(on);
                return CommandRegistry.prefix(codes, c.args()[1]);
            }
            if (c.args()[0].equalsIgnoreCase("disable")) {
                codes.retainAll(on);
                return CommandRegistry.prefix(codes, c.args()[1]);
            }
        }
        return List.of();
    }
}
