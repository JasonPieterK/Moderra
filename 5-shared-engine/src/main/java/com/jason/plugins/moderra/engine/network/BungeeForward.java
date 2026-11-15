package com.jason.plugins.moderra.engine.network;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * The well-known {@code BungeeCord} plugin message channel format (also understood by Velocity's legacy bridge).
 * A custom channel is not relayed to other backend servers by a proxy; the {@code Forward} sub-channel is:
 * <pre>UTF "Forward", UTF target ("ALL" or "ONLINE" or a server name), UTF sub-channel, short length, data</pre>
 * The receiving backend gets the same message on the {@code BungeeCord} channel without the first two words:
 * <pre>UTF sub-channel, short length, data</pre>
 * Pure functions, no server API.
 */
public final class BungeeForward {
    /** The proxy channel name. */
    public static final String CHANNEL = "BungeeCord";
    /** The data length is a short, so one payload must stay below this. */
    public static final int MAX_DATA = 32_000;

    private BungeeForward() {
    }

    /** Message for the {@code BungeeCord} channel that forwards {@code data} to every other server, or null if too long. */
    public static byte[] encodeForward(String subChannel, byte[] data) {
        if (data.length > MAX_DATA) {
            return null;
        }
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream(data.length + 32);
            DataOutputStream out = new DataOutputStream(bytes);
            out.writeUTF("Forward");
            out.writeUTF("ALL");
            out.writeUTF(subChannel);
            out.writeShort(data.length);
            out.write(data);
            return bytes.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException(ex); // cannot happen on a byte array
        }
    }

    /** The data of an incoming forwarded message for {@code subChannel}, or null for any other / damaged message. */
    public static byte[] decodeForwarded(String subChannel, byte[] message) {
        try {
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(message));
            if (!subChannel.equals(in.readUTF())) {
                return null;
            }
            int length = in.readUnsignedShort();
            byte[] data = new byte[length];
            in.readFully(data);
            return data;
        } catch (IOException | RuntimeException ex) {
            return null;
        }
    }
}
