package com.jason.plugins.moderra.engine;

import com.jason.plugins.moderra.engine.config.CoreSettings;
import java.util.Optional;

/** The original Moderra detection as the first pipeline stage: blocks a message that contains a listed word. */
public final class BadWordCheck implements TextCheck {
    public static final String ID = "badword";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public Optional<Hit> check(Message message, EffectiveSettings settings) {
        String word;
        if (Message.JOIN.equals(message.source())) {
            // player names have no spaces: they use the matcher that (if switched on) also looks inside the name
            word = settings.nameMatcher().find(message.text());
            if (word != null && settings.get(CoreSettings.JOIN_MATCH_INSIDE)) {
                // "Dickens99": a whitelisted word inside the name is taken out before the name is judged
                String rest = message.text();
                for (String safe : settings.get(CoreSettings.WHITELIST)) {
                    if (safe.length() >= 2) {
                        rest = java.util.regex.Pattern.compile(safe.trim(), java.util.regex.Pattern.LITERAL | java.util.regex.Pattern.CASE_INSENSITIVE
                            | java.util.regex.Pattern.UNICODE_CASE).matcher(rest).replaceAll("_");
                    }
                }
                if (!rest.equals(message.text())) {
                    word = settings.nameMatcher().find(rest);
                }
            }
        } else {
            word = settings.matcher().find(message.text());
        }
        return word == null ? Optional.empty() : Optional.of(Hit.block(ID, word, settings.tierOf(word), 1));
    }
}
