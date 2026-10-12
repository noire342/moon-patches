// SPDX-License-Identifier: GPL-3.0-only
// Copyright 2026 Moon Patches contributors.

package app.nyanime.rooms.core;

import com.google.gson.JsonParser;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.jupiter.api.Assertions.*;

/** Exercises the asynchronous final-write lifecycle without relying on a public relay. */
class NostrTransportCloseTest {
    static final class Socket extends WebSocketClient {
        final AtomicBoolean ended = new AtomicBoolean();
        final CountDownLatch written = new CountDownLatch(1);
        volatile String frame;
        Socket() { super(URI.create(RoomInvite.RELAYS.get(0))); }
        @Override public void send(String text) { frame = text; written.countDown(); }
        @Override public boolean isOpen() { return !ended.get(); }
        @Override public void close() { ended.set(true); }
        @Override public void onOpen(ServerHandshake handshake) {}
        @Override public void onMessage(String text) {}
        @Override public void onClose(int code, String reason, boolean remote) {}
        @Override public void onError(Exception error) {}
    }
    static final class Fixture implements AutoCloseable {
        final RoomIdentity identity = new RoomIdentity();
        final NostrTransport transport = new NostrTransport(new NostrCodec(identity, RoomIdentity.random(32),
                "close-regression", RoomProtocol.KIND), List.of(RoomInvite.RELAYS.get(0)), Runnable::run);
        final Socket socket = new Socket();
        final Object connection;
        Fixture() throws Exception {
            Class<?> type = Class.forName(NostrTransport.class.getName() + "$Connection");
            var constructor = type.getDeclaredConstructor(); constructor.setAccessible(true); connection = constructor.newInstance();
            field(type, "socket").set(connection, socket); field(type, "usable").set(connection, true);
            @SuppressWarnings("unchecked") Map<String,Object> connections = (Map<String,Object>) field(NostrTransport.class,"connections").get(transport);
            connections.put(RoomInvite.RELAYS.get(0), connection);
        }
        void receive(String id) throws Exception {
            CountDownLatch processed = new CountDownLatch(1);
            Method receive = NostrTransport.class.getDeclaredMethod("receive", String.class, connection.getClass(), WebSocketClient.class, String.class);
            receive.setAccessible(true);
            Method enqueue = NostrTransport.class.getDeclaredMethod("execute", Runnable.class); enqueue.setAccessible(true);
            enqueue.invoke(transport, (Runnable) () -> {
                try { receive.invoke(transport, RoomInvite.RELAYS.get(0), connection, socket, "[\"OK\",\""+id+"\",true,\"\"]"); }
                catch (ReflectiveOperationException error) { throw new RuntimeException(error); }
                finally { processed.countDown(); }
            });
            assertTrue(processed.await(1,TimeUnit.SECONDS));
        }
        @Override public void close() { transport.close(); identity.close(); }
    }
    static Field field(Class<?> type, String name) throws Exception { Field result=type.getDeclaredField(name); result.setAccessible(true); return result; }

    @Test void closeKeepsTheFinalEventUntilItsRelayAcknowledgement() throws Exception {
        try (Fixture fixture = new Fixture()) {
            fixture.transport.send("{\"type\":\"CLOSED\"}"); fixture.transport.close();
            assertTrue(fixture.socket.written.await(1,TimeUnit.SECONDS));
            assertFalse(fixture.socket.ended.get(), "The final event has not been acknowledged yet");
            String id=JsonParser.parseString(fixture.socket.frame).getAsJsonArray().get(1).getAsJsonObject().get("id").getAsString();
            fixture.receive("unrelated-event"); assertFalse(fixture.socket.ended.get());
            fixture.receive(id); assertTrue(fixture.socket.ended.get());
        }
    }
    @Test void unavailableRelayCannotKeepTheTransportOpenIndefinitely() throws Exception {
        try (Fixture fixture = new Fixture()) {
            fixture.transport.send("{\"type\":\"LEAVE\"}"); fixture.transport.close();
            assertTrue(fixture.socket.written.await(1,TimeUnit.SECONDS));
            long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(3);
            while (!fixture.socket.ended.get() && System.nanoTime()<deadline) Thread.sleep(10);
            assertTrue(fixture.socket.ended.get(), "The bounded drain must finish even without an OK");
        }
    }
}
