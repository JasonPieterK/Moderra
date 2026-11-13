package com.jason.plugins.antibadwords.engine;

import java.util.Optional;

/** The original AntiBadWords detection as the first pipeline stage: blocks a message that contains a listed word. */
public final class BadWordCheck implements TextCheck {
    public static final String ID = "badword";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public Optional<Hit> check(Message message, EffectiveSettings settings) {
        // player names have no spaces: they use the matcher that (if switched on) also looks inside the name
        String word = (Message.JOIN.equals(message.source()) ? settings.nameMatcher() : settings.matcher()).find(message.text());
        return word == null ? Optional.empty() : Optional.of(Hit.block(ID, word, settings.tierOf(word), 1));
    }
}
