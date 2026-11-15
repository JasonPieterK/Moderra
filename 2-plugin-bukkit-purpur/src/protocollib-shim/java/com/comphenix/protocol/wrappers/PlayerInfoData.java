package com.comphenix.protocol.wrappers;

import java.util.UUID;

/** COMPILE-TIME SHIM (names and signatures copied from the javap -public dump of the real class); not in the jar. */
public class PlayerInfoData {
    public PlayerInfoData(UUID profileId, int latency, boolean listed, EnumWrappers.NativeGameMode gameMode, WrappedGameProfile profile,
                          WrappedChatComponent displayName) {
    }

    public UUID getProfileId() {
        return null;
    }

    public WrappedGameProfile getProfile() {
        return null;
    }

    public int getLatency() {
        return 0;
    }

    public boolean isListed() {
        return false;
    }

    public EnumWrappers.NativeGameMode getGameMode() {
        return null;
    }

    public WrappedChatComponent getDisplayName() {
        return null;
    }
}
