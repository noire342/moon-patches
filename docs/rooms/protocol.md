# Rooms protocol v2

`room-core` is an Android-independent Java 17 module. The YouTube extension implements the player adapter and native panel. The code can be reused in a future Nyanime integration, but this release does not interoperate with Nyanime rooms.

Nostr NIP-01 events are signed using BIP-340 Schnorr signatures over secp256k1. Event kind 20972 carries room traffic and 20973 carries short-code discovery/admission traffic. The implementation validates event IDs, signatures, sizes, timestamps and routing before processing messages.

Room payloads use a custom AES-256-GCM envelope with a fresh random nonce. This is **not NIP-44**, and the event kinds are a project protocol rather than a standardized Nostr room service. A 256-bit random session secret derives the room topic; an eight-digit code locates a separate rendezvous. The host explicitly approves a request and sends a key grant encrypted using the host/guest ECDH-derived key. Compare the displayed fingerprint over a trusted channel before admission.

The host owns canonical state. Guests can control playback by default, with host permission changes. Commands include the current media identity so delayed actions cannot seek a different video. Sequence tracking and replay rejection apply to received state. Native player input is captured before applying heartbeat corrections; pending commands hold local state briefly to avoid snapping back while awaiting the host response. A lobby carries no media identity and does not open an empty video on guests.

The participant limit is eight and sessions expire after six hours. Loss of fresh host state pauses playback after the configured 6.5-second freshness window. SponsorBlock remains suspended throughout reconnecting/admission/active session states. No automatic host migration is implemented.

On closure, the transport waits for a relay acknowledgement of its final event before tearing down sockets, with a two-second upper bound when the relay is unavailable. This reduces lost ephemeral close/leave notifications; a network partition can still prevent delivery.

The transport uses several public relays and acknowledges writable connections. There are no guarantees about relay uptime, latency, retention or abuse resistance. The short code is enumerable; host approval is the security boundary. Admitted peers share a session key, so this design does not provide malicious-member isolation or forward secrecy for stored session traffic. It has not undergone an independent cryptographic audit.
