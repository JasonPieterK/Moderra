package com.djrapitops.plan.extension.annotation;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/** COMPILE-TIME SHIM (names and signatures copied from the javap -public dump of the real class); not in the jar. */
@Retention(RetentionPolicy.RUNTIME)
public @interface NumberProvider {
    String text();

    int priority() default 0;

    String description() default "";
}
