package com.comphenix.protocol;

/** COMPILE-TIME SHIM (names and signatures copied from the javap -public dump of the real class); not in the jar. */
public class PacketType {
    public static class Play {
        public static class Client {
            public static final PacketType B_EDIT = new PacketType();
            public static final PacketType ITEM_NAME = new PacketType();
            public static final PacketType UPDATE_SIGN = new PacketType();
        }

        public static class Server {
            public static final PacketType SYSTEM_CHAT = new PacketType();
            public static final PacketType CHAT = new PacketType();
            public static final PacketType DISGUISED_CHAT = new PacketType();
        }
    }
}
