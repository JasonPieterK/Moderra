package com.jason.plugins.moderra;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;
import org.junit.jupiter.api.Test;

class PlaceholdersTest {
    @Test
    void isCaseInsensitiveAndKeepsUnknownPlaceholders() {
        Map<String, String> values = Map.of("player", "Steve", "punishments", "blindness and slowness");
        assertEquals("Steve / Steve / blindness and slowness / {unknown}",
            Placeholders.apply("{player} / {PLAYER} / {PUNISHMENTS} / {unknown}", values));
    }

    @Test
    void replacementsAreLiteral() {
        assertEquals("a $1 b", Placeholders.apply("a {x} b", Map.of("x", "$1")));
    }
}
