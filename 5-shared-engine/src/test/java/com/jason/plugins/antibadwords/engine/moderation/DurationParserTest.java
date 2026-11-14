package com.jason.plugins.antibadwords.engine.moderation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class DurationParserTest {
    @Test
    void readsTheFormsStaffType() {
        assertEquals(30_000L, DurationParser.parse("30s"));
        assertEquals(600_000L, DurationParser.parse("10m"));
        assertEquals(5_400_000L, DurationParser.parse("1h30m"));
        assertEquals(172_800_000L, DurationParser.parse("2d"));
        assertEquals(604_800_000L, DurationParser.parse("1w"));
        assertEquals(5_400_000L, DurationParser.parse(" 1H 30M "));
        assertEquals(0L, DurationParser.parse("0s"));
    }

    @Test
    void permanentIsItsOwnValue() {
        for (String word : new String[] {"perm", "PERM", "permanent", "forever"}) {
            assertEquals(DurationParser.PERMANENT, DurationParser.parse(word));
        }
        assertTrue(DurationParser.isPermanent(DurationParser.PERMANENT));
        assertFalse(DurationParser.isPermanent(0));
    }

    @Test
    void refusesEverythingElse() {
        for (String bad : new String[] {null, "", "  ", "90", "10x", "m", "abc", "1h bad", "-5m", "1.5h", "99999999999s", "perm 1h"}) {
            assertThrows(IllegalArgumentException.class, () -> DurationParser.parse(bad), String.valueOf(bad));
            assertTrue(DurationParser.tryParse(bad).isEmpty());
        }
        assertEquals(600_000L, DurationParser.tryParse("10m").getAsLong());
    }

    @Test
    void formats() {
        assertEquals("1h30m", DurationParser.format(5_400_000L));
        assertEquals("2d", DurationParser.format(172_800_000L));
        assertEquals("permanent", DurationParser.format(DurationParser.PERMANENT));
        assertEquals(5_400_000L, DurationParser.parse(DurationParser.format(5_400_000L)));
    }
}
