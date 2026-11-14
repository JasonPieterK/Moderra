package github.scarsz.discordsrv.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * COMPILE-TIME SHIM, not part of the plugin jar (the maven-jar-plugin excludes {@code github/**}). It only has the name of
 * DiscordSRV's own annotation so {@code DiscordSrvListener} can be compiled without DiscordSRV's repository. At runtime the
 * real annotation of the installed DiscordSRV is used.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Subscribe {
}
