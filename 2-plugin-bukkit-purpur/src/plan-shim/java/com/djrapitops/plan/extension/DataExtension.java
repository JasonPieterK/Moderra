package com.djrapitops.plan.extension;

/** COMPILE-TIME SHIM (names and signatures copied from the javap -public dump of the real class); not in the jar. */
public interface DataExtension {
    default CallEvents[] callExtensionMethodsOn() {
        return new CallEvents[0];
    }
}
