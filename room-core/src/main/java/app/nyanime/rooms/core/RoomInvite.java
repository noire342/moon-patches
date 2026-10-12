// SPDX-License-Identifier: GPL-3.0-only
// Copyright 2026 Moon Patches contributors.

package app.nyanime.rooms.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

/** Private bearer invitation. Eight-digit codes are only a rendezvous requiring host admission. */
public final class RoomInvite {
    public static final Gson JSON = new GsonBuilder().disableHtmlEscaping().create();
    public static final List<String> RELAYS = List.of("wss://relay.damus.io", "wss://nos.lol", "wss://relay.primal.net");
    public int version = RoomProtocol.VERSION;
    public String secret;
    public String host;
    public long expires;
    public List<String> relays = RELAYS;

    public static RoomInvite create(String host, long now) {
        RoomInvite invite = new RoomInvite();
        invite.host = host;
        invite.secret = RoomIdentity.hex(RoomIdentity.random(32));
        invite.expires = now + RoomProtocol.SESSION_MS;
        return invite;
    }
    public boolean valid(long now) {
        return version == RoomProtocol.VERSION && secret != null && secret.matches("[0-9a-f]{64}")
                && host != null && host.matches("[0-9a-f]{64}") && expires > now && expires - now <= RoomProtocol.SESSION_MS
                && relays != null && relays.size() >= 1 && relays.size() <= 3 && relays.stream().distinct().count() == relays.size()
                && RELAYS.containsAll(relays);
    }
    public String topic() { return RoomIdentity.hex(RoomIdentity.hash(("nya-rooms-v2:" + secret).getBytes(StandardCharsets.UTF_8))); }
    public String link() { return "nyaroom://join/v2#" + Base64.getUrlEncoder().withoutPadding().encodeToString(JSON.toJson(this).getBytes(StandardCharsets.UTF_8)); }
    public static RoomInvite parse(String text, long now) {
        if (text == null || text.length() > 4096) throw new IllegalArgumentException("Invito non valido");
        var matcher = java.util.regex.Pattern.compile("nyaroom://join/v2#([A-Za-z0-9_-]+)").matcher(text.trim());
        if (!matcher.find()) throw new IllegalArgumentException("Incolla un invito della room");
        RoomInvite invite;
        try { invite = JSON.fromJson(new String(Base64.getUrlDecoder().decode(matcher.group(1)), StandardCharsets.UTF_8), RoomInvite.class); }
        catch (RuntimeException ex) { throw new IllegalArgumentException("Invito non valido"); }
        if (invite == null || !invite.valid(now)) throw new IllegalArgumentException("Invito scaduto o non compatibile");
        return invite;
    }
    public static String shortCode(String input) {
        if (input == null || input.length() > 4096) return "";
        String raw = input.trim().replaceAll("[\\s\\p{Zs}-]", "");
        if (raw.matches("[0-9]{8}")) return raw;
        var match = java.util.regex.Pattern.compile("nyaroom://join/v2#([0-9]{8})(?![A-Za-z0-9_-])").matcher(input);
        if (match.find()) return match.group(1);
        // Accept the complete invitation produced by the native share sheet, without
        // extracting arbitrary numbers from unrelated clipboard text.
        var shared = java.util.regex.Pattern.compile("(?i)\\bcodice(?: della room)?\\s*:\\s*([0-9]{4})[\\s\\p{Zs}-]?([0-9]{4})(?![A-Za-z0-9_-])").matcher(input);
        return shared.find() ? shared.group(1) + shared.group(2) : "";
    }
}
