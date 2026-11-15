package com.djrapitops.plan.capability;

import java.util.function.Consumer;

/** COMPILE-TIME SHIM (names and signatures copied from the javap -public dump of the real class); not in the jar. */
public interface CapabilityService {
    static CapabilityService getInstance() {
        throw new UnsupportedOperationException("shim");
    }

    default void registerEnableListener(Consumer<Boolean> listener) {
    }
}
