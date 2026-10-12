// SPDX-License-Identifier: GPL-3.0-only
// Copyright 2026 Moon Patches contributors.

package app.nyanime.rooms.core;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import static org.junit.jupiter.api.Assertions.*;
import static app.nyanime.rooms.core.RoomProtocol.*;

class RoomCoreTest {
    static final long NOW = 1_790_000_000_000L;
    static final class Clock implements RoomController.Clock {
        long wall = NOW, mono = 10_000;
        public long now() { return wall; }
        public long monotonic() { return mono; }
        void advance(long value) { wall += value; mono += value; }
    }
    static final class Player implements RoomController.Player {
        RoomController.Sample sample = new RoomController.Sample();
        int seeks, opens;
        Player() { sample.mediaId = "abcdefghijk"; sample.title = "Video"; sample.duration = 120_000; sample.ready = true; }
        public RoomController.Sample sample() {
            RoomController.Sample copy = new RoomController.Sample();
            copy.mediaId = sample.mediaId; copy.title = sample.title; copy.position = sample.position;
            copy.duration = sample.duration; copy.ready = sample.ready; copy.buffering = sample.buffering;
            copy.paused = sample.paused; copy.speed = sample.speed; return copy;
        }
        public boolean pause(boolean paused) { sample.paused = paused; return true; }
        public boolean seek(long millis) { sample.position = millis; seeks++; return true; }
        public void speed(double value) { sample.speed = value; }
        public void open(String provider, String id) { opens++; sample.mediaId = id; }
    }
    static final class Bus {
        final List<Link> links = new ArrayList<>();
        Link link(String sender) { Link link = new Link(this, sender); links.add(link); return link; }
        void inject(String sender, String body) {
            for (Link link : new ArrayList<>(links)) if (link.listener != null && !link.closed && !link.sender.equals(sender)) link.listener.message(sender, body);
        }
    }
    static final class Link implements RoomTransport {
        final Bus bus; final String sender;
        Listener listener; boolean closed, delayCommands;
        final List<String> sent = new ArrayList<>();
        Link(Bus bus, String sender) { this.bus = bus; this.sender = sender; }
        public void start(Listener listener) { this.listener = listener; listener.connection(1); }
        public void send(String plaintext) {
            sent.add(plaintext);
            if (!closed && !(delayCommands && RoomInvite.JSON.fromJson(plaintext, Message.class).type == Type.COMMAND)) bus.inject(sender, plaintext);
        }
        public void close() { closed = true; }
    }
    static final class Pair {
        final RoomIdentity hostId = new RoomIdentity(), guestId = new RoomIdentity();
        final Clock clock = new Clock(); final Bus bus = new Bus();
        final Player hostPlayer = new Player(), guestPlayer = new Player();
        final RoomInvite invite = RoomInvite.create(hostId.publicKey, clock.now());
        final Link hostLink = bus.link(hostId.publicKey), guestLink = bus.link(guestId.publicKey);
        final RoomController host, guest;
        Pair() { this(false); }
        Pair(boolean lobby) {
            if (lobby) hostPlayer.sample = new RoomController.Sample();
            host = new RoomController(invite, hostId.publicKey, "Host", hostPlayer, clock, hostLink, s -> {});
            guest = new RoomController(invite, guestId.publicKey, "Guest", guestPlayer, clock, guestLink, s -> {});
            host.admit(guestId.publicKey, "Guest"); host.start(); guest.start(); host.tick(); guest.tick();
        }
    }

    @Test void officialBip340VerificationVectors() throws Exception {
        try (var stream = getClass().getResourceAsStream("/bip340-test-vectors.csv")) {
            assertNotNull(stream);
            String[] lines = new String(stream.readAllBytes(), StandardCharsets.UTF_8).split("\\R");
            int tested = 0;
            for (int i = 1; i < lines.length; i++) {
                if (lines[i].isBlank()) continue;
                String[] cells = lines[i].split(",", 8);
                byte[] message = new byte[cells[4].length() / 2];
                for (int j = 0; j < message.length; j++) message[j] = (byte) Integer.parseInt(cells[4].substring(j * 2, j * 2 + 2), 16);
                assertEquals(cells[6].equals("TRUE"), RoomIdentity.verify(cells[2].toLowerCase(), message, cells[5].toLowerCase()), "Vector " + cells[0]);
                tested++;
            }
            assertTrue(tested >= 15);
        }
    }
    @Test void privateInvitationRoundTripAndRelayAllowlist() {
        RoomIdentity host = new RoomIdentity(); RoomInvite invite = RoomInvite.create(host.publicKey, NOW);
        RoomInvite restored = RoomInvite.parse("Entra: " + invite.link(), NOW);
        assertEquals(invite.topic(), restored.topic());
        assertThrows(IllegalArgumentException.class, () -> RoomInvite.parse(invite.link(), NOW + SESSION_MS));
        invite.relays = List.of("wss://127.0.0.1"); assertFalse(invite.valid(NOW));
        assertEquals("12345678", RoomInvite.shortCode("1234 5678"));
        assertEquals("12345678", RoomInvite.shortCode("Entra: nyaroom://join/v2#12345678"));
        assertEquals("", RoomInvite.shortCode("12345678x"));
    }

    @Test void pasteAcceptsOurShareMessageAndRejectsUnrelatedOrMalformedNumbers() {
        assertEquals("12345678", RoomInvite.shortCode("Guarda insieme su YouTube\nCodice: 12345678\nL'host approva l'accesso."));
        assertEquals("12345678", RoomInvite.shortCode("Codice della room: 1234\u00a05678"));
        assertEquals("12345678", RoomInvite.shortCode("1234\u202f5678"));
        assertEquals("", RoomInvite.shortCode("Numero ordine: 12345678"));
        assertEquals("", RoomInvite.shortCode("Codice: 123456789"));
        assertEquals("", RoomInvite.shortCode("Codice: 12345678x"));
        assertEquals("", RoomInvite.shortCode("Codice: 12345678_"));
    }
    @Test void signedEncryptedEventsRejectTamperingOtherRoomsAndExpiry() {
        RoomIdentity identity = new RoomIdentity(); RoomInvite invite = RoomInvite.create(identity.publicKey, NOW);
        NostrCodec codec = new NostrCodec(identity, RoomIdentity.bytes(invite.secret, 32), invite.topic(), KIND);
        JsonObject event = codec.seal("{\"title\":\"Privato\"}", NOW);
        assertFalse(event.toString().contains("Privato"));
        assertEquals("{\"title\":\"Privato\"}", codec.open(event, NOW).plaintext);
        assertNull(codec.open(event, NOW + 61_000));
        JsonObject modified = event.deepCopy(); modified.addProperty("created_at", NOW / 1000 + 1);
        assertNull(codec.open(modified, NOW));
        NostrCodec other = new NostrCodec(identity, RoomIdentity.random(32), invite.topic(), KIND);
        assertNull(other.open(event, NOW));
        modified = event.deepCopy(); modified.addProperty("content", "abc"); assertNull(codec.open(modified, NOW));
    }
    @Test void ecdhAdmissionKeysAreSymmetricAndBoundToPeersAndCode() {
        RoomIdentity a = new RoomIdentity(), b = new RoomIdentity();
        assertArrayEquals(a.admissionKey(b.publicKey, "12345678"), b.admissionKey(a.publicKey, "12345678"));
        assertFalse(java.util.Arrays.equals(a.admissionKey(b.publicKey, "12345678"), a.admissionKey(b.publicKey, "12345679")));
    }
    @Test void hostPlaybackControlsSynchronizeGuest() {
        Pair pair = new Pair();
        pair.host.command("play", 0); assertFalse(pair.guestPlayer.sample.paused);
        pair.host.command("seek", 40_000); assertEquals(40_000, pair.guestPlayer.sample.position);
        pair.clock.advance(1100); pair.host.command("pause", 0); assertTrue(pair.guestPlayer.sample.paused);
    }
    @Test void sharedControlsRequireHostPermission() {
        Pair pair = new Pair();
        pair.host.options(false, false);
        pair.guest.command("play", 0); assertTrue(pair.hostPlayer.sample.paused);
        pair.host.options(true, false); pair.guest.command("play", 0);
        assertFalse(pair.hostPlayer.sample.paused); assertFalse(pair.guestPlayer.sample.paused);
    }
    @Test void admittedGuestsCanControlPlaybackByDefault() {
        Pair pair = new Pair();
        assertTrue(pair.host.state().sharedControls); assertTrue(pair.guest.state().canControl());
        pair.guest.command("play", 0); assertFalse(pair.hostPlayer.sample.paused);
        pair.guest.command("pause", 0); assertTrue(pair.hostPlayer.sample.paused);
        pair.guest.command("seek", 20_000); assertEquals(20_000, pair.hostPlayer.sample.position);
    }
    @Test void guestsNativePausePlayAndSeekReachHost() {
        Pair pair = new Pair();
        pair.guestPlayer.sample.paused = false; pair.guest.tick();
        assertFalse(pair.hostPlayer.sample.paused);
        pair.clock.advance(1000); pair.guestPlayer.sample.paused = true; pair.guest.tick();
        assertTrue(pair.hostPlayer.sample.paused);
        pair.clock.advance(1000); pair.guestPlayer.sample.position = 35_000; pair.guest.tick();
        assertEquals(35_000, pair.hostPlayer.sample.position);
        assertEquals(3, commandCount(pair.guestLink));
    }
    @Test void hostCorrectionsDoNotEchoAsGuestCommands() {
        Pair pair = new Pair();
        pair.host.command("play", 0); pair.guest.tick();
        pair.clock.advance(1000); pair.guestPlayer.sample.position += 1000; pair.guest.tick();
        pair.host.command("seek", 45_000); pair.guest.tick();
        pair.clock.advance(1000); pair.guestPlayer.sample.position += 1000; pair.guest.tick();
        assertEquals(0, commandCount(pair.guestLink));
    }
    @Test void nativeCommandWaitsForHostEchoWithoutSnappingBack() {
        Pair pair = new Pair(); pair.guestLink.delayCommands = true;
        pair.guestPlayer.sample.paused = false; pair.guest.tick();
        assertFalse(pair.guestPlayer.sample.paused); assertTrue(pair.hostPlayer.sample.paused);
        pair.clock.advance(1000); pair.guestPlayer.sample.position += 1000; pair.guest.tick();
        assertFalse(pair.guestPlayer.sample.paused); assertEquals(1, commandCount(pair.guestLink));
        String command = pair.guestLink.sent.stream().filter(body -> RoomInvite.JSON.fromJson(body, Message.class).type == Type.COMMAND).findFirst().orElseThrow();
        pair.bus.inject(pair.guestId.publicKey, command);
        assertFalse(pair.hostPlayer.sample.paused); assertFalse(pair.guestPlayer.sample.paused);
    }
    @Test void unsharedNativeControlsCannotChangeHostPlayback() {
        Pair pair = new Pair(); pair.host.options(false, false);
        pair.guestPlayer.sample.paused = false; pair.guest.tick();
        assertTrue(pair.hostPlayer.sample.paused); assertTrue(pair.guestPlayer.sample.paused);
        assertEquals(0, commandCount(pair.guestLink));
    }
    @Test void heartbeatCannotEraseNativePauseOrSeekBeforeTick() {
        Pair pair = new Pair();
        pair.guestPlayer.sample.paused = false;
        pair.host.options(true, false); // Incoming heartbeat before the guest's next tick.
        assertFalse(pair.hostPlayer.sample.paused); assertFalse(pair.guestPlayer.sample.paused);
        pair.guest.tick(); pair.clock.advance(1100);
        pair.guestPlayer.sample.position = 40_000;
        pair.host.options(true, false);
        assertEquals(40_000, pair.hostPlayer.sample.position);
        assertEquals(2, commandCount(pair.guestLink));
    }
    @Test void commandForPreviousVideoCannotSeekNewHostVideo() {
        Pair pair = new Pair(); pair.guestLink.delayCommands = true;
        pair.guest.command("seek", 40_000);
        String command = pair.guestLink.sent.stream().filter(body -> RoomInvite.JSON.fromJson(body, Message.class).type == Type.COMMAND).findFirst().orElseThrow();
        pair.hostPlayer.sample.mediaId = "newvideo123"; pair.host.tick();
        pair.bus.inject(pair.guestId.publicKey, command);
        assertEquals(0, pair.hostPlayer.sample.position);
    }
    private static long commandCount(Link link) {
        return link.sent.stream().filter(body -> RoomInvite.JSON.fromJson(body, Message.class).type == Type.COMMAND).count();
    }
    @Test void roomCanStartWithoutVideoAndPublishSelectionLater() {
        Pair pair = new Pair(true);
        assertTrue(pair.host.state().active); assertTrue(pair.guest.state().active);
        assertEquals(Phase.WAITING, pair.host.state().phase); assertEquals(Phase.WAITING, pair.guest.state().phase);
        assertEquals("", pair.guest.state().mediaId); assertEquals(0, pair.guestPlayer.opens);
        pair.guest.command("play", 0); assertTrue(pair.hostPlayer.sample.paused);
        pair.hostPlayer.sample.mediaId = "newvideo123"; pair.hostPlayer.sample.duration = 120_000;
        pair.hostPlayer.sample.ready = true; pair.clock.advance(2100); pair.host.tick();
        assertEquals("newvideo123", pair.guest.state().mediaId); assertEquals(1, pair.guestPlayer.opens);
        pair.guest.command("play", 0); assertFalse(pair.hostPlayer.sample.paused);
        pair.host.close(); assertFalse(pair.guest.state().active);
    }
    @Test void lobbyMessageCannotSmugglePlaybackState() {
        Message message = new Message(); message.at = NOW; message.sequence = 1; message.type = Type.LOBBY;
        assertTrue(message.valid(NOW)); message.mediaId = "abcdefghijk"; assertFalse(message.valid(NOW));
        message.mediaId = ""; message.duration = 1000; assertFalse(message.valid(NOW));
        message.duration = 0; message.paused = false; assertFalse(message.valid(NOW));
    }
    @Test void connectionTextsKeepUnicodeAfterCompilation() {
        Pair pair = new Pair(); Bus discovery = new Bus();
        RoomAdmission.Factory factory = (identity, code) -> discovery.link(identity.publicKey);
        RoomAdmission host = new RoomAdmission(pair.hostId, "12345678", "Host", pair.invite, pair.host, pair.clock, factory, admissionListener(i -> {}));
        RoomAdmission guest = new RoomAdmission(new RoomIdentity(), "12345678", "Guest", null, null, pair.clock, factory, admissionListener(i -> {}));
        assertEquals("Connessione\u2026", guest.status());
        host.start(); assertEquals("La room \u00E8 pronta", host.status());
        guest.start(); assertEquals("Cerchiamo la room\u2026", guest.status());
        pair.clock.advance(5100); host.tick();
        assertEquals("Aspettiamo l\u2019approvazione dell\u2019host\u2026", guest.status());
        host.close(); guest.close();
    }
    @Test void unauthorizedPeerCannotCommandOrCloseRoom() {
        Pair pair = new Pair(); pair.host.options(true, false);
        Message command = new Message(); command.type = Type.COMMAND; command.command = "play"; command.sequence = 99; command.at = NOW;
        pair.bus.inject(new RoomIdentity().publicKey, RoomInvite.JSON.toJson(command));
        assertTrue(pair.hostPlayer.sample.paused);
        command.type = Type.CLOSED;
        pair.bus.inject(pair.guestId.publicKey, RoomInvite.JSON.toJson(command));
        assertTrue(pair.host.state().active);
    }
    @Test void duplicateAndReorderedTimelineCannotUndoLatestState() {
        Pair pair = new Pair();
        String old = pair.hostLink.sent.get(pair.hostLink.sent.size() - 1);
        pair.host.command("play", 0); pair.bus.inject(pair.hostId.publicKey, old);
        assertFalse(pair.guestPlayer.sample.paused);
    }
    @Test void missingHostPausesGuestAndRetainsRoomUntilRecovery() {
        Pair pair = new Pair(); pair.host.command("play", 0);
        pair.clock.advance(7000); pair.guest.tick();
        assertTrue(pair.guestPlayer.sample.paused); assertTrue(pair.guest.state().active);
        assertEquals(Phase.RECONNECTING, pair.guest.state().phase);
        pair.host.tick(); pair.guest.tick(); assertFalse(pair.guestPlayer.sample.paused);
    }
    @Test void waitForEveryonePausesAndRecoversAfterBuffering() {
        Pair pair = new Pair(); pair.host.options(false, true); pair.host.command("play", 0);
        pair.guestPlayer.sample.buffering = true; pair.clock.advance(3001); pair.guest.tick(); pair.host.tick();
        assertTrue(pair.hostPlayer.sample.paused);
        pair.guestPlayer.sample.buffering = false; pair.clock.advance(3001); pair.guest.tick(); pair.host.tick();
        assertFalse(pair.hostPlayer.sample.paused);
    }
    @Test void closingHostClosesGuestAndRestoresSpeed() {
        Pair pair = new Pair(); pair.guestPlayer.sample.speed = 1.05;
        pair.host.close(); assertFalse(pair.guest.state().active); assertEquals(1, pair.guestPlayer.sample.speed);
    }
    @Test void expiredSessionClosesAndInvalidMessagesAreRejected() {
        Pair pair = new Pair(); pair.clock.advance(SESSION_MS); pair.guest.tick();
        assertFalse(pair.guest.state().active);
        Message message = new Message(); message.at = NOW; message.sequence = 1; message.type = Type.STATUS;
        assertTrue(message.valid(NOW)); message.speed = Double.NaN; assertFalse(message.valid(NOW));
        message.speed = 1; message.members.add(new Member("fake", "Bad")); assertFalse(message.valid(NOW));
    }
    @Test void clockUsesLeastDelayedSamplesAndRejectsInvalidReplies() {
        RoomClock clock = new RoomClock();
        assertTrue(clock.record(1000, 1150, 1160, 1110)); assertEquals(100, clock.offset()); assertEquals(50, clock.latency());
        assertTrue(clock.record(1000, 2500, 2500, 4000)); assertEquals(100, clock.offset());
        assertFalse(clock.record(1000, 1000, 900, 1100));
        assertFalse(clock.record(1000, 1000, 1000, 9000));
    }
    @Test void driftCorrectionUsesGentleSpeedUntilLargeDeviation() {
        assertEquals(-1, RoomClock.correct(1000, 1080, 1, false, false).seek);
        assertEquals(1, RoomClock.correct(1000, 1080, 1, false, false).speed);
        assertEquals(-1, RoomClock.correct(1000, 1500, 1, false, false).seek);
        assertEquals(1.05, RoomClock.correct(1000, 1500, 1, false, false).speed);
        assertEquals(5000, RoomClock.correct(1000, 5000, 1, false, false).seek);
        assertEquals(1500, RoomClock.correct(1000, 1500, 1, true, false).seek);
    }
    @Test void shortCodeAdmissionRequiresExplicitHostApproval() {
        Pair pair = new Pair(); RoomIdentity joining = new RoomIdentity(); Bus discovery = new Bus();
        RoomAdmission.Factory factory = (identity, code) -> discovery.link(identity.publicKey);
        List<RoomInvite> granted = new ArrayList<>();
        RoomAdmission.Listener hostListener = admissionListener(i -> {});
        RoomAdmission host = new RoomAdmission(pair.hostId, "12345678", "Host", pair.invite, pair.host, pair.clock, factory, hostListener);
        RoomAdmission guest = new RoomAdmission(joining, "12345678", "New guest", null, null, pair.clock, factory, admissionListener(granted::add));
        host.start(); guest.start(); pair.clock.advance(5100); host.tick();
        assertEquals(1, host.requests().size()); assertTrue(granted.isEmpty());
        assertTrue(host.approve(joining.publicKey)); assertEquals(1, granted.size());
        assertEquals(pair.invite.secret, granted.get(0).secret);
        host.close(); guest.close();
    }
    private static RoomAdmission.Listener admissionListener(Consumer<RoomInvite> granted) {
        return new RoomAdmission.Listener() {
            public void changed() {}
            public void granted(RoomInvite invite) { granted.accept(invite); }
            public void failed(String message) { fail(message); }
        };
    }
}
