// SPDX-License-Identifier: GPL-3.0-only
// Copyright 2026 Moon Patches contributors.

package app.nyanime.rooms.core;

import java.util.ArrayList;
import java.util.List;

/** Versioned, provider-neutral playback protocol. No stream URLs, credentials or media bytes. */
public final class RoomProtocol {
    private RoomProtocol() {}
    public static final int VERSION = 2;
    public static final int KIND = 20972;
    public static final int DISCOVERY_KIND = 20973;
    public static final int MAX_MEMBERS = 8;
    public static final long SESSION_MS = 6 * 60 * 60 * 1000L;

    public enum Type { HELLO, STATUS, TIMELINE, LOBBY, COMMAND, PING, PONG, LEAVE, CLOSED }
    public enum Phase { IDLE, CONNECTING, WAITING, PLAYING, PAUSED, BUFFERING, RECONNECTING, CLOSED, ERROR }

    public static final class Member {
        public String id = "";
        public String name = "";
        public boolean ready;
        public boolean buffering;
        public long position;
        public long seen;
        public Member() {}
        public Member(String id, String name) { this.id = id; this.name = cleanName(name); }
    }

    public static final class Message {
        public int version = VERSION;
        public Type type;
        public long sequence;
        public long at;
        public String provider = "youtube";
        public String mediaId = "";
        public String title = "";
        public long position;
        public long duration;
        public boolean paused = true;
        public boolean ready;
        public boolean buffering;
        public double speed = 1;
        public String name = "";
        public String target = "";
        public String command = "";
        public long revision;
        public long ping;
        public long receivedAt;
        public boolean sharedControls;
        public boolean waitForEveryone;
        public List<Member> members = new ArrayList<>();

        public boolean valid(long now) {
            if (version != VERSION || type == null || sequence <= 0 || at < 0 || Math.abs(now - at) > 90_000
                    || provider == null || !provider.matches("[a-z][a-z0-9-]{0,31}")
                    || mediaId == null || mediaId.length() > 160 || title == null || title.length() > 240
                    || name == null || name.length() > 40 || target == null || target.length() > 64
                    || command == null || command.length() > 24 || revision < 0
                    || duration < 0 || duration > 86_400_000 || position < 0 || position > 86_400_000
                    || !Double.isFinite(speed) || speed < 0.25 || speed > 4
                    || members == null || members.size() > MAX_MEMBERS) return false;
            if (type == Type.TIMELINE && (mediaId.isEmpty() || duration == 0)) return false;
            if (type == Type.LOBBY && (!mediaId.isEmpty() || duration != 0 || position != 0
                    || !paused || ready || buffering)) return false;
            List<String> ids = new ArrayList<>();
            for (Member member : members) {
                if (member == null || member.id == null || !member.id.matches("[0-9a-f]{64}")
                        || member.name == null || member.name.length() > 40 || ids.contains(member.id)) return false;
                ids.add(member.id);
            }
            return true;
        }
    }

    public static String cleanName(String name) {
        String value = name == null ? "" : name.replaceAll("[\\p{Cntrl}\\p{Cf}]", "").trim();
        return value.isEmpty() ? "Ospite" : value.substring(0, Math.min(40, value.length()));
    }
}
