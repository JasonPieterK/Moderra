package com.jason.plugins.antibadwords.engine.gui.prompt;

import com.jason.plugins.antibadwords.engine.config.Setting;
import com.jason.plugins.antibadwords.engine.config.SettingType;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * One question to one player: "type a new value". Immutable description; start it with
 * {@link PromptService#start(java.util.UUID, TextPrompt)}.
 *
 * <p>The validator returns {@code null} when the text is fine, otherwise a short error text for the player (it must not
 * repeat a secret). The callback gets the accepted text. For a prompt built with
 * {@link #forSetting} the callback gets the parsed value instead (type of the setting).
 */
public final class TextPrompt {
    private final String title;
    private final String hint;
    private final boolean secret;
    private final Function<String, String> validator;
    private final Consumer<String> onText;
    private final Runnable onCancel;

    private TextPrompt(String title, String hint, boolean secret, Function<String, String> validator, Consumer<String> onText,
                       Runnable onCancel) {
        this.title = title == null ? "" : title;
        this.hint = hint == null ? "" : hint;
        this.secret = secret;
        this.validator = validator == null ? text -> null : validator;
        this.onText = onText;
        this.onCancel = onCancel == null ? () -> { } : onCancel;
    }

    /** A free text prompt. {@code onText} is called on the main thread with the accepted text. */
    public static TextPrompt of(String title, String hint, boolean secret, Function<String, String> validator, Consumer<String> onText,
                                Runnable onCancel) {
        if (onText == null) {
            throw new IllegalArgumentException("onText");
        }
        return new TextPrompt(title, hint, secret, validator, onText, onCancel);
    }

    /**
     * A prompt for the value of a setting: the text is checked with {@link Setting#require} (type, min, max, allowed
     * values; the error text of the setting is shown) and the callback receives the parsed value. Secret settings are
     * never echoed.
     */
    public static <T> TextPrompt forSetting(Setting<T> setting, Consumer<T> onValue, Runnable onCancel) {
        if (setting == null || onValue == null) {
            throw new IllegalArgumentException("setting and callback are required");
        }
        Function<String, String> validator = text -> {
            try {
                setting.require(text);
                return null;
            } catch (IllegalArgumentException ex) {
                return ex.getMessage();
            }
        };
        return new TextPrompt(setting.title(), setting.constraintText(), setting.type() == SettingType.SECRET, validator,
            text -> onValue.accept(setting.require(text)), onCancel);
    }

    public String title() {
        return title;
    }

    /** What kind of value is expected (shown under the title). */
    public String hint() {
        return hint;
    }

    /** Secret prompts never show the typed text (the engine itself never echoes any, this also tells renderers). */
    public boolean secret() {
        return secret;
    }

    /** {@code null} = accepted, else the error for the player. */
    String validate(String text) {
        try {
            String error = validator.apply(text);
            return error == null || error.isBlank() ? null : error;
        } catch (RuntimeException ex) {
            return "That value is not valid.";
        }
    }

    void accept(String text) {
        onText.accept(text);
    }

    void cancelled() {
        onCancel.run();
    }
}
