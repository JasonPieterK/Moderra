package com.jason.plugins.antibadwords;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class PunishmentExecutorTest {
    @Test
    void soundDescriptionKeepsEverythingAfterTheCategory() {
        assertEquals("villager no sound", PunishmentExecutor.describeSound("entity.villager.no"));
        assertEquals("anvil land sound", PunishmentExecutor.describeSound("minecraft:block.anvil.land"));
        assertEquals("note block pling sound", PunishmentExecutor.describeSound("block.note_block.pling"));
        assertEquals("pling sound", PunishmentExecutor.describeSound("pling"));
    }
}
