// SPDX-License-Identifier: GPL-3.0-only
// Copyright 2026 Moon Patches contributors.

package app.nyanime.rooms.core;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/** Second independent participant for an Android↔desktop live-relay smoke test. No keys in logs. */
public final class RoomLiveProbe {
    public static void main(String[] args) throws Exception {
        if (args.length != 2) throw new IllegalArgumentException("code and command-file required");
        ScheduledExecutorService loop = Executors.newSingleThreadScheduledExecutor();
        RoomIdentity identity = new RoomIdentity();
        RoomController.Clock clock = new RoomController.Clock() {
            public long now() { return System.currentTimeMillis(); }
            public long monotonic() { return System.nanoTime() / 1_000_000; }
        };
        ProbePlayer player = new ProbePlayer();
        RoomController[] controller = { null };
        String[] previous = { "" };
        RoomAdmission admission = new RoomAdmission(identity, args[0], "Prova PC", null, null, clock,
                (id, code) -> new NostrTransport(new NostrCodec(id, RoomAdmission.discoveryKey(code), RoomAdmission.discoveryTopic(code), RoomProtocol.DISCOVERY_KIND), RoomInvite.RELAYS, loop),
                new RoomAdmission.Listener() {
                    public void changed() { }
                    public void failed(String message) { System.out.println("ADMISSION_FAILED " + message); loop.shutdownNow(); }
                    public void granted(RoomInvite invite) {
                        System.out.println("APPROVED");
                        controller[0] = new RoomController(invite, identity.publicKey, "Prova PC", player, clock,
                                new NostrTransport(new NostrCodec(identity, RoomIdentity.bytes(invite.secret, 32), invite.topic(), RoomProtocol.KIND), invite.relays, loop), state -> {
                            String summary = state.active + " " + state.phase + " " + state.mediaId + " members=" + state.members.size() + " shared=" + state.sharedControls;
                            if (!summary.equals(previous[0])) { previous[0] = summary; System.out.println("STATE " + summary); }
                        });
                        controller[0].start();
                    }
                });
        loop.execute(admission::start);
        Path commands = Path.of(args[1]);
        loop.scheduleAtFixedRate(() -> {
            admission.tick();
            if (controller[0] != null) {
                controller[0].tick();
                if (Files.isRegularFile(commands)) {
                    try {
                        String[] cmd = Files.readString(commands).trim().split(" "); Files.delete(commands);
                        if (cmd[0].equals("close")) { controller[0].close(); System.out.println("PROBE_CLOSED"); loop.shutdown(); }
                        else { controller[0].command(cmd[0], cmd.length > 1 ? Long.parseLong(cmd[1]) : 0); System.out.println("COMMAND " + cmd[0]); }
                    } catch (Exception error) { System.out.println("COMMAND_FAILED"); }
                }
            }
        }, 250, 250, TimeUnit.MILLISECONDS);
        System.out.println("IDENTITY " + identity.publicKey.substring(0,4) + " " + identity.publicKey.substring(4,8));
        if (!loop.awaitTermination(8, TimeUnit.MINUTES)) {
            loop.execute(() -> { admission.close(); if (controller[0] != null) controller[0].close(); loop.shutdown(); });
            loop.awaitTermination(10, TimeUnit.SECONDS);
        }
        identity.close();
    }
    private static final class ProbePlayer implements RoomController.Player {
        final RoomController.Sample value = new RoomController.Sample(); long last = System.nanoTime();
        public RoomController.Sample sample() {
            long now = System.nanoTime(); if (!value.paused) value.position += (long)((now-last)/1_000_000.0*value.speed); last=now; return value;
        }
        public boolean pause(boolean paused) { sample(); value.paused=paused; System.out.println("PLAYER_PAUSED " + paused); return true; }
        public boolean seek(long position) { sample(); value.position=position; System.out.println("PLAYER_SEEK " + position); return true; }
        public void speed(double speed) { sample(); value.speed=speed; }
        public void open(String provider, String mediaId) { sample(); value.provider=provider; value.mediaId=mediaId; value.duration=3_600_000; value.ready=true; System.out.println("PLAYER_OPEN " + mediaId); }
    }
}
