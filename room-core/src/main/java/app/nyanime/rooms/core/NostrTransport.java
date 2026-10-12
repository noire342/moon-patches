// SPDX-License-Identifier: GPL-3.0-only
// Copyright 2026 Moon Patches contributors.

package app.nyanime.rooms.core;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/** Redundant live relay connections. EOSE is insufficient: an accepted signed write proves usability. */
public final class NostrTransport implements RoomTransport {
    private final NostrCodec codec;
    private final List<String> urls;
    private final Executor dispatcher;
    private final ScheduledExecutorService io = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "NyaRoomsRelay"); thread.setDaemon(true); return thread;
    });
    private final Map<String, Connection> connections = new HashMap<>();
    private final LinkedHashMap<String, Boolean> seen = new LinkedHashMap<>();
    private Listener listener;
    private volatile boolean closed;
    private volatile boolean closing;
    private int lastCount = -1;
    // Accessed on io. A final CLOSED/LEAVE event must reach a relay before teardown.
    private String lastPublishedId = "", lastAcceptedId = "";

    private static final class Connection {
        WebSocketClient socket;
        String probe = "";
        boolean usable;
        int attempts;
    }

    public NostrTransport(NostrCodec codec, List<String> urls, Executor dispatcher) {
        if (urls == null || urls.isEmpty() || urls.size() > 3 || !RoomInvite.RELAYS.containsAll(urls)) throw new IllegalArgumentException("Unsupported relay");
        this.codec = codec; this.urls = new ArrayList<>(urls); this.dispatcher = dispatcher;
    }
    @Override public void start(Listener listener) {
        if (this.listener != null) throw new IllegalStateException("Already started");
        this.listener = listener;
        execute(() -> { refresh(); for (String url : urls) connect(url); });
    }
    private void execute(Runnable action) {
        if (!closed) { try { io.execute(() -> { if (!closed) action.run(); }); } catch (java.util.concurrent.RejectedExecutionException ignored) {} }
    }
    private void connect(String url) {
        if (closing) return;
        Connection connection = connections.computeIfAbsent(url, ignored -> new Connection());
        WebSocketClient socket = new WebSocketClient(URI.create(url)) {
            @Override public void onOpen(ServerHandshake handshake) { execute(() -> opened(url, connection, this)); }
            @Override public void onMessage(String text) { execute(() -> receive(url, connection, this, text)); }
            @Override public void onClose(int code, String reason, boolean remote) { execute(() -> disconnected(url, connection, this)); }
            @Override public void onError(Exception ex) { /* onClose owns reconnection. No payloads/keys in logs. */ }
        };
        connection.socket = socket; connection.usable = false;
        socket.setConnectionLostTimeout(20);
        socket.connect();
        io.schedule(() -> {
            if (!closed && connection.socket == socket && !connection.usable) socket.close();
        }, 20, TimeUnit.SECONDS);
    }
    private void opened(String url, Connection connection, WebSocketClient socket) {
        if (connection.socket != socket || closing) { socket.close(); return; }
        JsonObject filter = new JsonObject();
        JsonArray kinds = new JsonArray(); kinds.add(codec.kind); filter.add("kinds", kinds);
        JsonArray topics = new JsonArray(); topics.add(codec.topic); filter.add("#x", topics); filter.addProperty("limit", 0);
        JsonArray request = new JsonArray(); request.add("REQ"); request.add("nya-room"); request.add(filter); socket.send(request.toString());
        JsonObject probe = codec.seal("{\"probe\":true}", System.currentTimeMillis());
        connection.probe = probe.get("id").getAsString();
        send(socket, probe);
    }
    private void receive(String url, Connection connection, WebSocketClient socket, String text) {
        if (connection.socket != socket || closed) return;
        if (text.length() > NostrCodec.MAX_EVENT_BYTES + 512) { socket.close(1009, "Event too large"); return; }
        try {
            JsonArray frame = JsonParser.parseString(text).getAsJsonArray();
            if (frame.isEmpty()) return;
            String type = frame.get(0).getAsString();
            if (type.equals("OK") && frame.size() >= 4) {
                if (frame.get(2).getAsBoolean() && frame.get(1).getAsString().equals(lastPublishedId)) {
                    lastAcceptedId = lastPublishedId;
                    if (closing) { finishClose(); return; }
                }
                if (frame.get(1).getAsString().equals(connection.probe)) {
                    if (!frame.get(2).getAsBoolean()) { socket.close(); return; }
                    connection.usable = true; connection.attempts = 0; refresh();
                } else if (!frame.get(2).getAsBoolean()) {
                    // A previously writable relay may change its policy or impose rate limits.
                    socket.close();
                }
            } else if (type.equals("CLOSED")) {
                socket.close();
            } else if (type.equals("EVENT") && frame.size() == 3 && frame.get(1).getAsString().equals("nya-room")) {
                JsonObject event = frame.get(2).getAsJsonObject();
                String id = event.get("id").getAsString();
                if (seen.containsKey(id)) return;
                NostrCodec.Received received = codec.open(event, System.currentTimeMillis());
                if (received == null) return;
                seen.put(id, true); if (seen.size() > 512) seen.remove(seen.keySet().iterator().next());
                dispatcher.execute(() -> { if (!closed) listener.message(received.sender, received.plaintext); });
            }
        } catch (RuntimeException ignored) { /* Malformed remote data is never an instruction or a fatal error. */ }
    }
    private void disconnected(String url, Connection connection, WebSocketClient socket) {
        if (connection.socket != socket || closing) return;
        connection.usable = false; refresh();
        long delay = Math.min(30, 1L << Math.min(5, connection.attempts++));
        io.schedule(() -> { if (!closed && connection.socket == socket) connect(url); }, delay, TimeUnit.SECONDS);
    }
    private void refresh() {
        int count = (int) connections.values().stream().filter(c -> c.usable).count();
        if (count != lastCount) { lastCount = count; dispatcher.execute(() -> { if (!closed) listener.connection(count); }); }
    }
    @Override public void send(String plaintext) {
        if (closing) return;
        JsonObject event = codec.seal(plaintext, System.currentTimeMillis());
        execute(() -> {
            lastPublishedId = event.get("id").getAsString(); lastAcceptedId = "";
            for (Connection connection : connections.values()) {
                if (connection.usable && connection.socket.isOpen()) {
                    try { send(connection.socket, event); } catch (RuntimeException ignored) { connection.socket.close(); }
                }
            }
        });
    }
    private static void send(WebSocketClient socket, JsonObject event) {
        JsonArray frame = new JsonArray(); frame.add("EVENT"); frame.add(event); socket.send(frame.toString());
    }
    @Override public void close() {
        if (closing) return;
        closing = true;
        // Enqueue after the final write. Closing a socket immediately can discard an
        // ephemeral event before the relay handles it, so drain until its OK or timeout.
        io.execute(() -> {
            if (closed) return;
            if (lastPublishedId.isEmpty() || lastPublishedId.equals(lastAcceptedId)
                    || connections.values().stream().noneMatch(c -> c.usable && c.socket.isOpen())) finishClose();
            else io.schedule(this::finishClose, 2, TimeUnit.SECONDS);
        });
    }
    private void finishClose() {
        if (closed) return;
        closed = true;
        for (Connection connection : connections.values()) if (connection.socket != null) connection.socket.close();
        connections.clear(); seen.clear(); codec.clear(); io.shutdownNow();
    }
}
