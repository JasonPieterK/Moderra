package github.scarsz.discordsrv.api.commands;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * COMPILE-TIME SHIM, not part of the plugin jar. Members as listed by the real DiscordSRV class (javap of the plugin-api-dump job).
 * They have no defaults here so the plugin must set every one: it then does not depend on the defaults of the installed version.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface SlashCommand {
    String path();

    SlashCommandPriority priority();

    boolean deferReply();

    boolean deferEphemeral();

    boolean ignoreAcknowledged();
}
