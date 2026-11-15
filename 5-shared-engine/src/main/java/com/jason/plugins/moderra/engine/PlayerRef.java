package com.jason.plugins.moderra.engine;

import java.util.UUID;

/** An online player as the engine sees one: just the id and the name. */
public record PlayerRef(UUID id, String name) {
}
