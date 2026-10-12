// SPDX-License-Identifier: GPL-3.0-only
// Copyright 2026 Moon Patches contributors.

package app.nyanime.rooms.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** NTP-style offset estimation; prefer the least delayed samples, never trust a single slow reply. */
public final class RoomClock {
    private static final class Sample {
        long delay, offset;
        Sample(long delay, long offset) { this.delay = delay; this.offset = offset; }
    }
    private final List<Sample> samples = new ArrayList<>();
    public boolean record(long sent, long hostReceived, long hostSent, long received) {
        long delay = (received - sent) - (hostSent - hostReceived);
        if (hostSent < hostReceived || received < sent || delay < 0 || delay > 4000) return false;
        long offset = ((hostReceived - sent) + (hostSent - received)) / 2;
        if (Math.abs(offset) > 90_000) return false;
        samples.add(new Sample(delay, offset));
        if (samples.size() > 12) samples.remove(0);
        return true;
    }
    public long offset() {
        if (samples.isEmpty()) return 0;
        List<Sample> sorted = new ArrayList<>(samples); sorted.sort(Comparator.comparingLong(s -> s.delay));
        List<Long> offsets = new ArrayList<>();
        for (int i = 0; i < Math.min(3, sorted.size()); i++) offsets.add(sorted.get(i).offset);
        offsets.sort(Long::compare); return offsets.get(offsets.size() / 2);
    }
    public long latency() { return samples.stream().mapToLong(s -> s.delay).min().orElse(0) / 2; }
    public boolean calibrated() { return !samples.isEmpty(); }

    public static final class Correction {
        public final long seek;
        public final double speed;
        Correction(long seek, double speed) { this.seek = seek; this.speed = speed; }
    }
    public static Correction correct(long current, long target, double baseSpeed, boolean paused, boolean discontinuity) {
        long drift = target - current;
        if (discontinuity || Math.abs(drift) >= 1800 || (paused && Math.abs(drift) >= 180)) return new Correction(target, baseSpeed);
        if (paused || Math.abs(drift) <= 120) return new Correction(-1, baseSpeed);
        double adjustment = Math.max(-0.05, Math.min(0.05, drift / 10_000.0));
        return new Correction(-1, Math.max(0.25, Math.min(4, baseSpeed * (1 + adjustment))));
    }
}
