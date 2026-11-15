package com.djrapitops.plan.extension;

import java.util.Optional;

/** COMPILE-TIME SHIM (names and signatures copied from the javap -public dump of the real class); not in the jar. */
public interface ExtensionService {
    static ExtensionService getInstance() {
        throw new UnsupportedOperationException("shim");
    }

    Optional<Caller> register(DataExtension extension);

    void unregister(DataExtension extension);
}
