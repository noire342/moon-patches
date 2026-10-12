// SPDX-License-Identifier: GPL-3.0-only
// Copyright 2026 Moon Patches contributors.

package app.nyanime.rooms.core;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/** Signed NIP-01 ephemeral events carrying an application AES-256-GCM envelope. */
public final class NostrCodec {
    public static final int MAX_EVENT_BYTES = 24_576;
    public static final class Received {
        public final String sender;
        public final String plaintext;
        public Received(String sender, String plaintext) { this.sender = sender; this.plaintext = plaintext; }
    }
    private final RoomIdentity identity;
    private final byte[] key;
    public final String topic;
    public final int kind;
    public NostrCodec(RoomIdentity identity, byte[] key, String topic, int kind) {
        this.identity = identity; this.key = key.clone(); this.topic = topic; this.kind = kind;
    }
    public JsonObject seal(String plaintext, long now) {
        long at = now / 1000;
        JsonArray tags = new JsonArray();
        JsonArray topicTag = new JsonArray(); topicTag.add("x"); topicTag.add(topic); tags.add(topicTag);
        JsonArray expirationTag = new JsonArray(); expirationTag.add("expiration"); expirationTag.add(Long.toString(at + 60)); tags.add(expirationTag);
        String content = encrypt(key, plaintext, aad(identity.publicKey, at));
        byte[] hash = eventHash(identity.publicKey, at, kind, tags, content);
        JsonObject event = new JsonObject();
        event.addProperty("id", RoomIdentity.hex(hash)); event.addProperty("pubkey", identity.publicKey);
        event.addProperty("created_at", at); event.addProperty("kind", kind); event.add("tags", tags);
        event.addProperty("content", content); event.addProperty("sig", RoomIdentity.hex(identity.sign(hash)));
        if (event.toString().length() > MAX_EVENT_BYTES) throw new IllegalArgumentException("Event too large");
        return event;
    }
    public Received open(JsonObject event, long now) {
        try {
            if (event.toString().length() > MAX_EVENT_BYTES || event.get("kind").getAsInt() != kind) return null;
            String sender = event.get("pubkey").getAsString();
            long at = event.get("created_at").getAsLong();
            if (at < 0 || Math.abs(now / 1000 - at) > 90) return null;
            JsonArray tags = event.getAsJsonArray("tags");
            if (tags.size() > 8) return null;
            boolean topicFound = false, expiryFound = false;
            for (var tag : tags) {
                var parts = tag.getAsJsonArray();
                if (parts.size() != 2) return null;
                String type = parts.get(0).getAsString(), value = parts.get(1).getAsString();
                if (type.equals("x")) { if (topicFound || !value.equals(topic)) return null; topicFound = true; }
                if (type.equals("expiration")) {
                    long expiration = Long.parseLong(value);
                    if (expiryFound || expiration <= now / 1000 || expiration > at + 60) return null;
                    expiryFound = true;
                }
            }
            if (!topicFound || !expiryFound) return null;
            String content = event.get("content").getAsString();
            byte[] hash = eventHash(sender, at, kind, tags, content);
            if (!RoomIdentity.hex(hash).equals(event.get("id").getAsString())
                    || !RoomIdentity.verify(sender, hash, event.get("sig").getAsString())) return null;
            return new Received(sender, decrypt(key, content, aad(sender, at)));
        } catch (RuntimeException ex) { return null; }
    }
    public void clear() { java.util.Arrays.fill(key, (byte) 0); }
    private byte[] aad(String sender, long at) { return ("nya-rooms-v2:" + kind + ":" + topic + ":" + sender + ":" + at).getBytes(StandardCharsets.UTF_8); }
    public static byte[] eventHash(String pubkey, long at, int kind, JsonArray tags, String content) {
        JsonArray canonical = new JsonArray(); canonical.add(0); canonical.add(pubkey); canonical.add(at);
        canonical.add(kind); canonical.add(tags); canonical.add(content);
        return RoomIdentity.hash(RoomInvite.JSON.toJson(canonical).getBytes(StandardCharsets.UTF_8));
    }
    public static String encrypt(byte[] key, String value, byte[] aad) {
        try {
            byte[] nonce = RoomIdentity.random(12);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, nonce));
            cipher.updateAAD(aad);
            return Base64.getUrlEncoder().withoutPadding().encodeToString(RoomIdentity.concat(nonce, cipher.doFinal(value.getBytes(StandardCharsets.UTF_8))));
        } catch (Exception ex) { throw new IllegalStateException("Encryption failed", ex); }
    }
    public static String decrypt(byte[] key, String value, byte[] aad) {
        try {
            if (value.length() > MAX_EVENT_BYTES) throw new IllegalArgumentException("Ciphertext too large");
            byte[] encrypted = Base64.getUrlDecoder().decode(value);
            if (encrypted.length < 28) throw new IllegalArgumentException("Ciphertext too short");
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, java.util.Arrays.copyOf(encrypted, 12)));
            cipher.updateAAD(aad);
            return new String(cipher.doFinal(encrypted, 12, encrypted.length - 12), StandardCharsets.UTF_8);
        } catch (Exception ex) { throw new IllegalArgumentException("Authentication failed"); }
    }
}
