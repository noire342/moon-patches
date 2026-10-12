// SPDX-License-Identifier: GPL-3.0-only
// Copyright 2026 Moon Patches contributors.

package app.nyanime.rooms.core;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/** Bounded real-relay lobby/control smoke test using two owned identities and simulated players. */
public final class RoomLobbyLiveProbe {
    public static void main(String[] args) throws Exception {
        var loop = Executors.newSingleThreadScheduledExecutor();
        var result = new CompletableFuture<Void>();
        var hostId = new RoomIdentity(); var guestId = new RoomIdentity();
        RoomController.Clock clock = new RoomController.Clock() {
            public long now() { return System.currentTimeMillis(); }
            public long monotonic() { return System.nanoTime() / 1_000_000; }
        };
        var hostPlayer = new ProbePlayer(); var guestPlayer = new ProbePlayer();
        var invite = RoomInvite.create(hostId.publicKey, clock.now());
        String code = RoomAdmission.newCode();
        RoomController[] rooms = new RoomController[2]; RoomAdmission[] admissions = new RoomAdmission[2];
        RoomAdmission.Factory discovery = (identity, roomCode) -> new NostrTransport(new NostrCodec(identity,
                RoomAdmission.discoveryKey(roomCode), RoomAdmission.discoveryTopic(roomCode), RoomProtocol.DISCOVERY_KIND), RoomInvite.RELAYS, loop);
        rooms[0] = new RoomController(invite, hostId.publicKey, "Host prova", hostPlayer, clock, transport(hostId, invite, loop), state -> {});
        admissions[0] = new RoomAdmission(hostId, code, "Host prova", invite, rooms[0], clock, discovery, new RoomAdmission.Listener() {
            public void changed() {
                for (var request : admissions[0].requests()) {
                    if (!request.id.equals(guestId.publicKey)) throw new IllegalStateException("Unexpected test participant");
                    if (!admissions[0].approve(request.id)) throw new IllegalStateException("Admission failed");
                }
            }
            public void granted(RoomInvite value) { throw new IllegalStateException("Host cannot receive a grant"); }
            public void failed(String message) { result.completeExceptionally(new IllegalStateException(message)); }
        });
        admissions[1] = new RoomAdmission(guestId, code, "Ospite prova", null, null, clock, discovery, new RoomAdmission.Listener() {
            public void changed() {}
            public void granted(RoomInvite value) {
                rooms[1] = new RoomController(value, guestId.publicKey, "Ospite prova", guestPlayer, clock, transport(guestId, value, loop), state -> {});
                rooms[1].start();
            }
            public void failed(String message) { result.completeExceptionally(new IllegalStateException(message)); }
        });
        int[] stage = {0}; long[] nextAction = {0};
        loop.execute(() -> { rooms[0].start(); admissions[0].start(); admissions[1].start(); });
        loop.scheduleAtFixedRate(() -> {
            if (result.isDone()) return;
            try {
                rooms[0].tick(); admissions[0].tick(); admissions[1].tick();
                if (rooms[1] == null) return;
                rooms[1].tick();
                switch (stage[0]) {
                    case 0 -> {
                        if (rooms[1].state().phase != RoomProtocol.Phase.WAITING || rooms[1].state().relays == 0) return;
                        if (!rooms[1].state().sharedControls || guestPlayer.opens != 0 || rooms[1].state().members.size() != 2) throw new IllegalStateException("Invalid lobby state");
                        System.out.println("PASS lobby_without_video_and_default_shared_controls");
                        hostPlayer.open("youtube", "abcdefghijk"); stage[0] = 1;
                    }
                    case 1 -> {
                        if (!guestPlayer.value.ready || !guestPlayer.value.mediaId.equals("abcdefghijk")) return;
                        System.out.println("PASS host_video_opens_for_guest"); rooms[1].command("play", 0); stage[0] = 2;
                    }
                    case 2 -> {
                        if (hostPlayer.sample().paused || guestPlayer.sample().paused) return;
                        System.out.println("PASS guest_play_without_enabling_shared_option"); guestPlayer.pause(true); stage[0] = 3;
                    }
                    case 3 -> {
                        if (!hostPlayer.sample().paused || !guestPlayer.sample().paused) return;
                        System.out.println("PASS guest_native_pause_reaches_host"); nextAction[0] = clock.monotonic() + 1100; stage[0] = 4;
                    }
                    case 4 -> {
                        if (clock.monotonic() < nextAction[0]) return;
                        guestPlayer.seek(30_000); stage[0] = 5;
                    }
                    case 5 -> {
                        if (Math.abs(hostPlayer.sample().position - 30_000) > 1000) return;
                        System.out.println("PASS guest_native_seek_reaches_host"); rooms[0].close(); stage[0] = 6;
                    }
                    case 6 -> {
                        if (rooms[1].state().active) return;
                        System.out.println("PASS host_close_ends_guest_session"); result.complete(null);
                    }
                    default -> throw new IllegalStateException("Invalid test stage");
                }
            } catch (Exception error) { result.completeExceptionally(error); }
        }, 250, 250, TimeUnit.MILLISECONDS);
        try { result.get(100, TimeUnit.SECONDS); }
        finally {
            loop.submit(() -> {
                for (var admission : admissions) if (admission != null) admission.close();
                for (var room : rooms) if (room != null) room.close();
            }).get(5, TimeUnit.SECONDS);
            loop.shutdownNow(); hostId.close(); guestId.close();
        }
    }
    private static RoomTransport transport(RoomIdentity identity, RoomInvite invite, java.util.concurrent.Executor dispatcher) {
        return new NostrTransport(new NostrCodec(identity, RoomIdentity.bytes(invite.secret, 32), invite.topic(), RoomProtocol.KIND), invite.relays, dispatcher);
    }
    private static final class ProbePlayer implements RoomController.Player {
        final RoomController.Sample value = new RoomController.Sample(); long last = System.nanoTime(); int opens;
        public RoomController.Sample sample() {
            long now = System.nanoTime(); if (!value.paused) value.position += (long) ((now - last) / 1_000_000.0 * value.speed); last = now; return value;
        }
        public boolean pause(boolean paused) { sample(); value.paused = paused; return true; }
        public boolean seek(long position) { sample(); value.position = position; return true; }
        public void speed(double speed) { sample(); value.speed = speed; }
        public void open(String provider, String id) { sample(); opens++; value.provider = provider; value.mediaId = id; value.duration = 120_000; value.ready = true; }
    }
}
