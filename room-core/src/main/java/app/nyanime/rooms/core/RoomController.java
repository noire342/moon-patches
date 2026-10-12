// SPDX-License-Identifier: GPL-3.0-only
// Copyright 2026 Moon Patches contributors.

package app.nyanime.rooms.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import static app.nyanime.rooms.core.RoomProtocol.*;

/** Single-dispatcher host-authoritative room engine, independent of YouTube, Android and transport. */
public final class RoomController implements AutoCloseable {
    public interface Clock { long now(); long monotonic(); }
    public interface Player {
        Sample sample();
        boolean pause(boolean paused);
        boolean seek(long millis);
        void speed(double speed);
        void open(String provider, String mediaId);
    }
    public static final class Sample {
        public String provider = "youtube", mediaId = "", title = "";
        public long position, duration;
        public boolean paused = true, ready, buffering, ended;
        public double speed = 1;
    }
    public static final class State {
        public boolean active, host, sharedControls, waitForEveryone;
        public Phase phase = Phase.IDLE;
        public String title = "", mediaId = "", message = "";
        public int relays;
        public long latency, drift;
        public List<Member> members = new ArrayList<>();
        public boolean canControl() { return host || sharedControls; }
    }
    private final RoomInvite invite;
    private final String self, name;
    private final Player player;
    private final Clock clock;
    private final RoomTransport transport;
    private final Consumer<State> changed;
    private final Map<String, Member> members = new LinkedHashMap<>();
    private final Map<String, Long> sequences = new HashMap<>();
    private final RoomClock sync = new RoomClock();
    private final State state = new State();
    private long sequence, revision, appliedRevision = -1, lastSent, lastPing, lastTimeline, lastTick;
    private long pendingPing, pendingPingAt;
    private Message timeline;
    private Sample previous;
    private long applyingUntil, lastSeek, lastOpen, lastCorrection;
    private long previousAt, pendingCommandUntil, pendingCommandRevision;
    private boolean waitHold;
    private boolean desiredPaused = true;
    private double originalSpeed = 1;

    public RoomController(RoomInvite invite, String self, String name, Player player, Clock clock,
                          RoomTransport transport, Consumer<State> changed) {
        if (!invite.valid(clock.now())) throw new IllegalArgumentException("Expired room");
        this.invite = invite; this.self = self; this.name = cleanName(name); this.player = player;
        this.clock = clock; this.transport = transport; this.changed = changed;
        state.active = true; state.host = self.equals(invite.host); state.phase = Phase.CONNECTING;
        state.sharedControls = state.host;
        Sample initial = player.sample(); originalSpeed = initial.speed;
        if (state.host && hasVideo(initial)) { state.mediaId = initial.mediaId; state.title = initial.title; }
        members.put(invite.host, new Member(invite.host, state.host ? this.name : "Host"));
        members.put(self, new Member(self, this.name));
    }
    public void start() {
        lastTick = clock.monotonic();
        transport.start(new RoomTransport.Listener() {
            @Override public void message(String sender, String plaintext) { receive(sender, plaintext); }
            @Override public void connection(int writableRelays) {
                if (!state.active) return;
                state.relays = writableRelays;
                if (writableRelays > 0) { sendStatus(Type.HELLO); if (state.host) publish(true); else ping(); }
                update();
            }
        });
        update();
    }
    /** Admission is a host-only operation. Possessing a room key does not authorize playback commands. */
    public boolean admit(String id, String name) {
        if (!state.active || !state.host || !id.matches("[0-9a-f]{64}") || (!members.containsKey(id) && members.size() >= MAX_MEMBERS)) return false;
        members.putIfAbsent(id, new Member(id, name));
        members.get(id).seen = clock.monotonic();
        publish(true); return true;
    }
    public void options(boolean shared, boolean wait) {
        if (!state.host || !state.active) return;
        state.sharedControls = shared; state.waitForEveryone = wait;
        if (!wait && waitHold) { waitHold = false; applyPause(desiredPaused); }
        publish(true); update();
    }
    public void command(String command, long position) {
        if (!state.active || !state.canControl() || state.mediaId.isEmpty()) return;
        if (state.host) applyCommand(command, position);
        else {
            Message message = base(Type.COMMAND); message.command = command; message.position = Math.max(0, position);
            message.mediaId = state.mediaId;
            pendingCommandRevision = timeline == null ? -1 : timeline.revision;
            pendingCommandUntil = clock.monotonic() + 2000;
            message.target = invite.host; send(message);
        }
    }
    private void applyCommand(String command, long position) {
        switch (command) {
            case "play" -> { desiredPaused = false; waitHold = false; applyPause(false); }
            case "pause" -> { desiredPaused = true; waitHold = false; applyPause(true); }
            case "seek" -> { Sample sample = player.sample(); if (sample.duration > 0) player.seek(Math.min(position, Math.max(0, sample.duration - 500))); }
            default -> { return; }
        }
        applyingUntil = clock.monotonic() + 900; revision++; publish(false); update();
    }
    private void receive(String sender, String plaintext) {
        if (!state.active || plaintext.length() > 16_384 || sender.equals(self)) return;
        Message message;
        try { message = RoomInvite.JSON.fromJson(plaintext, Message.class); }
        catch (RuntimeException ex) { return; }
        if (message == null || !message.valid(clock.now()) || !members.containsKey(sender)
                || message.sequence <= sequences.getOrDefault(sender, 0L)) return;
        sequences.put(sender, message.sequence);
        if (!message.target.isEmpty() && !message.target.equals(self)) return;
        boolean fromHost = sender.equals(invite.host);
        if (message.type == Type.CLOSED && fromHost) { finish(Phase.CLOSED, "L’host ha chiuso la room"); return; }
        if (message.type == Type.LEAVE) {
            if (fromHost) finish(Phase.CLOSED, "L’host ha lasciato la room");
            else if (state.host) { members.remove(sender); publish(true); update(); }
            return;
        }
        if (message.type == Type.PING && state.host) {
            Message reply = base(Type.PONG); reply.target = sender; reply.ping = message.ping;
            reply.receivedAt = clock.now(); send(reply); return;
        }
        if (message.type == Type.PONG && fromHost && message.ping == pendingPing && pendingPing != 0) {
            sync.record(pendingPingAt, message.receivedAt, message.at, clock.now()); pendingPing = 0; return;
        }
        if (message.type == Type.COMMAND && state.host && state.sharedControls && !state.mediaId.isEmpty()
                && (message.mediaId.isEmpty() || message.mediaId.equals(state.mediaId)) && clock.now() - message.at < 10_000) {
            applyCommand(message.command, message.position); return;
        }
        if ((message.type == Type.STATUS || message.type == Type.HELLO) && state.host) {
            Member member = members.get(sender); member.name = cleanName(message.name); member.seen = clock.monotonic();
            member.ready = message.ready && message.mediaId.equals(state.mediaId); member.buffering = message.buffering;
            member.position = message.position;
            if (message.type == Type.HELLO) publish(true);
            update(); return;
        }
        if ((message.type == Type.TIMELINE || message.type == Type.LOBBY)
                && fromHost && !state.host && message.revision >= appliedRevision) {
            if (message.members.stream().noneMatch(m -> m.id.equals(self))) return;
            captureNativeControl(clock.monotonic());
            // A synchronous transport can deliver the command echo during capture.
            if (message.revision < appliedRevision || message.sequence < sequences.getOrDefault(sender, 0L)) return;
            timeline = message; lastTimeline = clock.monotonic();
            state.sharedControls = message.sharedControls; state.waitForEveryone = message.waitForEveryone;
            if (message.revision > pendingCommandRevision || !message.sharedControls) pendingCommandUntil = 0;
            members.clear(); for (Member member : message.members) members.put(member.id, member);
            state.title = message.title; state.mediaId = message.mediaId;
            applyTimeline(); update();
        }
    }
    public void tick() {
        if (!state.active) return;
        long now = clock.monotonic();
        if (clock.now() >= invite.expires) { finish(Phase.CLOSED, "La room è scaduta"); return; }
        boolean woke = now - lastTick > 8000; lastTick = now;
        Sample sample = player.sample();
        if (state.host) {
            state.mediaId = hasVideo(sample) ? sample.mediaId : "";
            state.title = hasVideo(sample) ? sample.title : "";
        }
        Member own = members.get(self);
        if (own != null) {
            own.ready = hasVideo(sample) && sample.mediaId.equals(state.mediaId);
            own.buffering = own.ready && sample.buffering; own.position = sample.position; own.seen = now;
        }
        if (state.host) {
            members.values().removeIf(m -> !m.id.equals(self) && m.seen > 0 && now - m.seen > 60_000);
            for (Member member : members.values()) if (!member.id.equals(self) && now - member.seen > 8000) member.ready = false;
            boolean missing = members.values().stream().anyMatch(m -> !m.ready || m.buffering);
            if (state.waitForEveryone && missing && !sample.paused && sample.ready && !waitHold) {
                desiredPaused = false; waitHold = true; applyPause(true); revision++;
            } else if (waitHold && !missing) { waitHold = false; applyPause(desiredPaused); revision++; }
            boolean discontinuity = previous != null && (!sample.mediaId.equals(previous.mediaId)
                    || (now >= applyingUntil && sample.paused != previous.paused)
                    || (now >= applyingUntil && Math.abs(sample.position - previous.position - expectedAdvance(now)) > 2000));
            if (discontinuity) { revision++; if (!waitHold) desiredPaused = sample.paused; }
            if (state.relays > 0 && (discontinuity || now - lastSent >= 2000 || woke)) publish(false);
        } else {
            if (state.relays > 0 && (now - lastSent >= 3000 || woke)) sendStatus(Type.STATUS);
            if (state.relays > 0 && (now - lastPing >= 10_000 || woke)) ping();
            if (timeline != null && now - lastTimeline > 6500) {
                // Retain session and SponsorBlock suppression; do not continue with a stale host clock.
                applyPause(true); player.speed(timeline.speed);
            } else if (timeline != null) {
                if (!woke) captureNativeControl(now);
                applyTimeline();
            }
        }
        previous = snapshot(player.sample()); previousAt = now; update();
    }
    /** Capture before both periodic correction and incoming heartbeats, so neither erases native input. */
    private void captureNativeControl(long now) {
        if (state.host || !state.sharedControls || state.relays == 0 || timeline == null || timeline.type != Type.TIMELINE
                || now - lastTimeline > 6500 || previous == null || now < pendingCommandUntil) return;
        Sample sample = player.sample();
        if (!sample.ready || !previous.ready || !sample.mediaId.equals(state.mediaId) || !sample.mediaId.equals(previous.mediaId)) return;
        if (sample.paused != previous.paused && sample.paused != timeline.paused) command(sample.paused ? "pause" : "play", 0);
        else if (now >= applyingUntil && Math.abs(sample.position - previous.position - expectedAdvance(now)) > 2000) command("seek", sample.position);
    }
    private double expectedAdvance(long now) {
        return previous == null || previous.paused ? 0 : Math.max(0, Math.min(8000, now - previousAt)) * previous.speed;
    }
    private static Sample snapshot(Sample source) {
        Sample copy = new Sample(); copy.provider = source.provider; copy.mediaId = source.mediaId; copy.title = source.title;
        copy.position = source.position; copy.duration = source.duration; copy.paused = source.paused;
        copy.ready = source.ready; copy.buffering = source.buffering; copy.ended = source.ended; copy.speed = source.speed;
        return copy;
    }
    private void applyTimeline() {
        if (timeline == null || !state.active) return;
        Sample local = player.sample(); long now = clock.monotonic();
        if (timeline.type == Type.LOBBY) { applyPause(true); appliedRevision = timeline.revision; return; }
        if (state.sharedControls && now < pendingCommandUntil) return;
        if (!local.mediaId.equals(timeline.mediaId)) {
            applyPause(true);
            if (now - lastOpen > 4000) { lastOpen = now; player.open(timeline.provider, timeline.mediaId); }
            return;
        }
        if (!local.ready || local.buffering) return;
        long elapsed = timeline.paused ? 0 : Math.max(0, Math.min(6000, clock.now() + sync.offset() - timeline.at));
        long target = Math.min(Math.max(0, timeline.duration - 500), timeline.position + (long) (elapsed * timeline.speed));
        state.drift = target - local.position;
        boolean newRevision = timeline.revision != appliedRevision;
        RoomClock.Correction correction = RoomClock.correct(local.position, target, timeline.speed, timeline.paused, newRevision);
        if (correction.seek >= 0 && (newRevision || now - lastSeek >= 1000)) {
            if (player.seek(correction.seek)) { lastSeek = now; appliedRevision = timeline.revision; applyingUntil = now + 900; }
        } else if (correction.seek < 0) appliedRevision = timeline.revision;
        if (now - lastCorrection > 750 && Math.abs(local.speed - correction.speed) > 0.005) {
            player.speed(correction.speed); lastCorrection = now;
        }
        if (local.paused != timeline.paused) { applyPause(timeline.paused); applyingUntil = now + 900; }
    }
    private void applyPause(boolean paused) { if (player.sample().paused != paused) player.pause(paused); }
    private static boolean hasVideo(Sample sample) {
        return sample.ready && !sample.mediaId.isEmpty() && sample.duration > 0 && sample.duration <= 86_400_000;
    }
    private Message base(Type type) {
        Message message = new Message(); message.type = type; message.sequence = ++sequence; message.at = clock.now(); return message;
    }
    private void send(Message message) { transport.send(RoomInvite.JSON.toJson(message)); }
    private void sendStatus(Type type) {
        Sample sample = player.sample(); Message message = base(type);
        message.name = name; message.provider = sample.provider; message.mediaId = sample.mediaId;
        message.ready = sample.ready; message.buffering = sample.buffering; message.position = Math.max(0, sample.position);
        send(message); lastSent = clock.monotonic();
    }
    private void publish(boolean force) {
        if (!state.active || !state.host || state.relays == 0) return;
        Sample sample = player.sample();
        boolean video = hasVideo(sample);
        state.mediaId = video ? sample.mediaId : ""; state.title = video ? sample.title : "";
        Message message = base(video ? Type.TIMELINE : Type.LOBBY); message.provider = sample.provider;
        if (video) {
            message.mediaId = sample.mediaId;
            message.title = sample.title.substring(0, Math.min(240, sample.title.length()));
            message.position = Math.max(0, sample.position); message.duration = sample.duration;
            message.paused = sample.paused; message.speed = Math.max(0.25, Math.min(4, sample.speed));
        }
        message.revision = revision;
        message.sharedControls = state.sharedControls; message.waitForEveryone = state.waitForEveryone;
        message.members = new ArrayList<>(members.values()); send(message); lastSent = clock.monotonic();
    }
    private void ping() {
        pendingPing = ++sequence; pendingPingAt = clock.now();
        Message message = base(Type.PING); message.target = invite.host; message.ping = pendingPing;
        send(message); lastPing = clock.monotonic();
    }
    private void update() {
        if (state.active) {
            Sample sample = player.sample();
            boolean lostHost = !state.host && (lastTimeline == 0 || clock.monotonic() - lastTimeline > 6500);
            if (state.relays == 0 || lostHost) { state.phase = Phase.RECONNECTING; state.message = "Connessione alla room…"; }
            else if (state.mediaId.isEmpty()) {
                state.phase = Phase.WAITING;
                state.message = state.host ? "Room pronta · Scegli un video" : "Aspettiamo che l’host scelga un video…";
            }
            else if (!sample.ready || (!state.host && !sample.mediaId.equals(state.mediaId))) { state.phase = Phase.WAITING; state.message = "Preparazione del video…"; }
            else if (sample.buffering || waitHold) { state.phase = Phase.BUFFERING; state.message = waitHold ? "Aspettiamo tutti…" : "Caricamento del video…"; }
            else { state.phase = sample.paused ? Phase.PAUSED : Phase.PLAYING; state.message = sample.paused ? "In pausa" : "Guardiamo insieme"; }
        }
        state.latency = sync.latency(); state.members = new ArrayList<>(members.values()); changed.accept(state);
    }
    private void finish(Phase phase, String message) {
        if (!state.active) return;
        state.active = false; state.phase = phase; state.message = message;
        transport.close(); player.speed(Math.max(0.25, Math.min(4, originalSpeed))); update();
    }
    @Override public void close() {
        if (state.active) { Message message = base(state.host ? Type.CLOSED : Type.LEAVE); send(message); finish(Phase.CLOSED, "Hai lasciato la room"); }
    }
    public State state() { return state; }
}
