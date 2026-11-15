package com.comphenix.protocol.events;

import com.comphenix.protocol.reflect.StructureModifier;
import com.comphenix.protocol.wrappers.PlayerInfoData;
import com.comphenix.protocol.wrappers.WrappedChatComponent;
import java.util.List;

/** COMPILE-TIME SHIM (names and signatures copied from the javap -public dump of the real class); not in the jar. */
public abstract class AbstractStructure {
    public StructureModifier<Object> getModifier() {
        return null;
    }

    public StructureModifier<WrappedChatComponent> getChatComponents() {
        return null;
    }

    public StructureModifier<List<PlayerInfoData>> getPlayerInfoDataLists() {
        return null;
    }
}
