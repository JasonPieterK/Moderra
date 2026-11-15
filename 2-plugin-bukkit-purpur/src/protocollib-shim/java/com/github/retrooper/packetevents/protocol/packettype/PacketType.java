package com.github.retrooper.packetevents.protocol.packettype;

/** COMPILE-TIME SHIM (names and signatures copied from the javap -public dump of the real class); not in the jar. */
public final class PacketType {
    public static final class Play {
        public enum Client implements PacketTypeCommon {
            EDIT_BOOK, NAME_ITEM, UPDATE_SIGN
        }

        public enum Server implements PacketTypeCommon {
            CHAT_MESSAGE, SYSTEM_CHAT_MESSAGE, TEAMS, PLAYER_INFO_UPDATE
        }
    }
}
