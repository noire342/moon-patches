// SPDX-License-Identifier: GPL-3.0-only
// Copyright 2026 Moon Patches contributors.

package app.nyanime.rooms.core;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import static app.nyanime.rooms.core.RoomProtocol.*;

/** Short-code discovery. Nicknames and full invitations are pairwise encrypted with ephemeral ECDH. */
public final class RoomAdmission implements AutoCloseable {
    public interface Listener {
        void changed();
        void granted(RoomInvite invite);
        void failed(String message);
    }
    public interface Factory { RoomTransport create(RoomIdentity identity, String code); }
    public static final class Request {
        public final String id, name;
        Request(String id, String name) { this.id = id; this.name = cleanName(name); }
        public String fingerprint() { return id.substring(0, 4) + " " + id.substring(4, 8); }
    }
    private static final class Discovery {
        int version = VERSION;
        String op = "", target = "", payload = "";
        long at, sequence;
    }
    private final RoomIdentity identity;
    private final String code, name;
    private final RoomInvite hostInvite;
    private final RoomController hostController;
    private final RoomController.Clock clock;
    private final Listener listener;
    private final RoomTransport transport;
    private final Map<String, Request> pending = new LinkedHashMap<>();
    private final Map<String, Long> sequences = new LinkedHashMap<>();
    private final Map<String, String> grants = new LinkedHashMap<>();
    private final Map<String, Boolean> rejected = new LinkedHashMap<>();
    private long sequence, lastSent, started;
    private String chosenHost = "";
    private boolean active = true;
    private int relays;

    public RoomAdmission(RoomIdentity identity, String code, String name, RoomInvite hostInvite,
                         RoomController hostController, RoomController.Clock clock, Factory factory, Listener listener) {
        if (!code.matches("[0-9]{8}")) throw new IllegalArgumentException("Codice non valido");
        this.identity = identity; this.code = code; this.name = cleanName(name); this.hostInvite = hostInvite;
        this.hostController = hostController; this.clock = clock; this.listener = listener;
        started = clock.monotonic(); transport = factory.create(identity, code);
    }
    public void start() {
        transport.start(new RoomTransport.Listener() {
            @Override public void message(String sender, String plaintext) { receive(sender, plaintext); }
            @Override public void connection(int count) { relays = count; if (count > 0) announce(); listener.changed(); }
        });
    }
    public static String newCode() { return String.format(java.util.Locale.ROOT, "%08d", new SecureRandom().nextInt(100_000_000)); }
    public static byte[] discoveryKey(String code) { return RoomIdentity.hash(("nya-rooms-discovery-key-v2:" + code).getBytes(StandardCharsets.UTF_8)); }
    public static String discoveryTopic(String code) { return RoomIdentity.hex(RoomIdentity.hash(("nya-rooms-discovery-v2:" + code).getBytes(StandardCharsets.UTF_8))); }
    public java.util.List<Request> requests() { return new ArrayList<>(pending.values()); }
    public String code() { return code; }
    public String ownFingerprint() { return identity.publicKey.substring(0, 4) + " " + identity.publicKey.substring(4, 8); }
    public String status() {
        if (relays == 0) return "Connessione…";
        if (hostInvite != null) return "La room è pronta";
        return chosenHost.isEmpty() ? "Cerchiamo la room…" : "Aspettiamo l’approvazione dell’host…";
    }
    public void tick() {
        if (!active) return;
        if (hostInvite == null && clock.monotonic() - started > 90_000) {
            close(); listener.failed("La room non risponde. Controlla il codice e riprova."); return;
        }
        if (relays > 0 && clock.monotonic() - lastSent >= 5000) announce();
    }
    private void announce() {
        if (!active) return;
        if (hostInvite != null) send("OFFER", "", "");
        else if (!chosenHost.isEmpty()) {
            byte[] key = identity.admissionKey(chosenHost, code);
            try { send("REQUEST", chosenHost, NostrCodec.encrypt(key, name, aad(identity.publicKey, chosenHost))); }
            finally { java.util.Arrays.fill(key, (byte) 0); }
        }
        lastSent = clock.monotonic();
    }
    private void receive(String sender, String plaintext) {
        if (!active || sender.equals(identity.publicKey) || plaintext.length() > 8192) return;
        try {
            Discovery message = RoomInvite.JSON.fromJson(plaintext, Discovery.class);
            if (message == null || message.version != VERSION || message.sequence <= 0 || message.at < 0
                    || Math.abs(clock.now() - message.at) > 30_000 || message.op == null || message.target == null || message.payload == null
                    || message.payload.length() > 6000 || !sender.matches("[0-9a-f]{64}")) return;
            if (message.sequence <= sequences.getOrDefault(sender, 0L)) return;
            if (sequences.size() >= 32 && !sequences.containsKey(sender)) return;
            sequences.put(sender, message.sequence);
            if (!message.target.isEmpty() && !message.target.equals(identity.publicKey)) return;
            if (message.op.equals("OFFER") && hostInvite == null) {
                if (chosenHost.isEmpty()) { chosenHost = sender; announce(); listener.changed(); }
            } else if (message.op.equals("REQUEST") && hostInvite != null) {
                if (rejected.containsKey(sender)) { send("REJECT", sender, ""); return; }
                if (grants.containsKey(sender)) { send("GRANT", sender, grants.get(sender)); return; }
                if (pending.size() >= MAX_MEMBERS || pending.containsKey(sender)) return;
                byte[] key = identity.admissionKey(sender, code);
                try {
                    String nickname = NostrCodec.decrypt(key, message.payload, aad(sender, identity.publicKey));
                    if (nickname.length() > 40) return;
                    pending.put(sender, new Request(sender, nickname)); listener.changed();
                } finally { java.util.Arrays.fill(key, (byte) 0); }
            } else if (message.op.equals("GRANT") && hostInvite == null && sender.equals(chosenHost)) {
                byte[] key = identity.admissionKey(sender, code);
                try {
                    RoomInvite invite = RoomInvite.parse(NostrCodec.decrypt(key, message.payload, aad(sender, identity.publicKey)), clock.now());
                    if (!invite.host.equals(sender)) return;
                    close(); listener.granted(invite);
                } finally { java.util.Arrays.fill(key, (byte) 0); }
            } else if (message.op.equals("REJECT") && hostInvite == null && sender.equals(chosenHost)) {
                close(); listener.failed("L’host non ha accettato la richiesta");
            }
        } catch (RuntimeException ignored) { /* Signed but invalid admission data. */ }
    }
    public boolean approve(String id) {
        Request request = pending.get(id);
        if (!active || hostInvite == null || request == null || !hostController.admit(id, request.name)) return false;
        byte[] key = identity.admissionKey(id, code);
        try {
            String grant = NostrCodec.encrypt(key, hostInvite.link(), aad(identity.publicKey, id));
            grants.put(id, grant); pending.remove(id); send("GRANT", id, grant); listener.changed(); return true;
        } finally { java.util.Arrays.fill(key, (byte) 0); }
    }
    public void reject(String id) {
        if (!active || hostInvite == null || !pending.containsKey(id)) return;
        pending.remove(id); rejected.put(id, true); send("REJECT", id, ""); listener.changed();
    }
    private byte[] aad(String sender, String target) { return ("nya-rooms-grant-v2:" + code + ":" + sender + ":" + target).getBytes(StandardCharsets.UTF_8); }
    private void send(String op, String target, String payload) {
        Discovery message = new Discovery(); message.op = op; message.target = target; message.payload = payload;
        message.at = clock.now(); message.sequence = ++sequence; transport.send(RoomInvite.JSON.toJson(message));
    }
    @Override public void close() { if (active) { active = false; transport.close(); pending.clear(); grants.clear(); rejected.clear(); } }
}
