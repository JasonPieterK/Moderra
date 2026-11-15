package com.jason.plugins.antibadwords.engine;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jason.plugins.antibadwords.engine.command.CommandSender;
import com.jason.plugins.antibadwords.engine.network.BungeeForward;
import com.jason.plugins.antibadwords.engine.network.PluginMessagingChannel;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class NetworkMessagingTest {
    @TempDir
    Path dir;

    @Test
    void forwardEncodingIsTheBungeeFormat() throws Exception {
        byte[] data = "{\"a\":1}".getBytes(StandardCharsets.UTF_8);
        byte[] msg = BungeeForward.encodeForward("antibadwords:sync", data);
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(msg));
        assertEquals("Forward", in.readUTF());
        assertEquals("ALL", in.readUTF());
        assertEquals("antibadwords:sync", in.readUTF());
        assertEquals(data.length, in.readUnsignedShort());
        assertArrayEquals(data, in.readNBytes(data.length));
        assertEquals(0, in.available());
        assertNull(BungeeForward.encodeForward("x", new byte[BungeeForward.MAX_DATA + 1]));
    }

    @Test
    void incomingForwardedMessageIsDecoded() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        out.writeUTF("antibadwords:sync");
        out.writeShort(3);
        out.write(new byte[] {1, 2, 3});
        assertArrayEquals(new byte[] {1, 2, 3}, BungeeForward.decodeForwarded("antibadwords:sync", bytes.toByteArray()));
        assertNull(BungeeForward.decodeForwarded("other", bytes.toByteArray()));
        assertNull(BungeeForward.decodeForwarded("antibadwords:sync", new byte[] {0, 1}));
        assertNull(BungeeForward.decodeForwarded("antibadwords:sync", new byte[0]));
    }

    private static final class Out implements CommandSender {
        final List<String> lines = new ArrayList<>();
        final boolean allowed;

        Out(boolean allowed) {
            this.allowed = allowed;
        }

        public String name() {
            return "CONSOLE";
        }

        public UUID id() {
            return null;
        }

        public boolean has(String p) {
            return allowed;
        }

        public void send(String m) {
            lines.add(m);
        }
    }

    @Test
    void queuesWhileNobodyIsOnlineAndStatusCommand() throws Exception {
        Fixture f = new Fixture(dir);
        f.start();
        Path file = f.file("integrations.yml");
        String text = Files.readString(file);
        int from = text.indexOf("network:");
        text = text.substring(0, from) + text.substring(from).replaceFirst("(?m)^(\\s+mode:).*$", "$1 \"plugin-messaging\"").replaceFirst("(?m)^(\\s+server-id:).*$", "$1 \"lobby\"");
        Files.writeString(file, text);
        AtomicBoolean online = new AtomicBoolean(false);
        List<byte[]> wire = new ArrayList<>();
        f.engine.network().setTransport(new PluginMessagingChannel.Transport() {
            public void send(String channel, byte[] payload) {
                wire.add(payload);
            }

            public boolean available() {
                return online.get();
            }
        });
        f.engine.reload();
        UUID bob = UUID.nameUUIDFromBytes("Bob".getBytes());
        f.engine.mutes().mute(bob, "Bob", 600_000, "x", "Admin");
        assertEquals(0, wire.size());
        assertEquals(1, f.engine.network().queued());
        Out out = new Out(true);
        f.engine.commands().execute(out, "abw", new String[] {"network"});
        String all = String.join("\n", out.lines);
        assertTrue(all.contains("plugin-messaging") && all.contains("lobby") && all.contains("Queued records"), all);
        online.set(true);
        f.engine.network().poll();
        assertEquals(1, wire.size());
        assertEquals(0, f.engine.network().queued());
        Out denied = new Out(false);
        f.engine.commands().execute(denied, "abw", new String[] {"network"});
        assertFalse(String.join("\n", denied.lines).contains("Server id"));
    }
}
