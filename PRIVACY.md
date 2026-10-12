# Privacy

Nya Rooms connects directly to public TLS Nostr relays: `relay.damus.io`, `nos.lol` and `relay.primal.net`. Moon Patches has no server to operate. Relays are independent services with their own policies and availability.

Relays see your IP address, connection times, ephemeral public keys, event timestamps, event kinds, routing tags, the short-code rendezvous topic and ciphertext sizes. They may log or retain these even when an event uses an ephemeral kind. The discovery protocol exposes the host public key, expiry and admission routing information; the short code is an invitation locator, not an encryption secret.

Room messages contain video identifiers/titles, playback state, nicknames, membership and permissions, encrypted using the session key. Admitted members receive that key and can read room traffic. Do not use a room as a confidential chat or assume protection against a malicious admitted participant. Host approval and fingerprint verification are required to establish trust.

Each session generates a fresh identity and encryption key. Keys, invitations, membership and playback state are held in memory, not persisted by the rooms feature. The chosen nickname is retained locally in app preferences. Clipboard access occurs when you press Paste; Copy/Share places the room code or invitation text into the system clipboard/share sheet.

YouTube still streams each video independently and its existing account/network behavior continues. Other base patches may contact their own services; see their settings and third-party notices. Moon does not add telemetry, analytics or an account backend. Closing the host room stops the session; relay-side logging or messages already received by peers cannot be revoked.

See [protocol.md](docs/rooms/protocol.md) for the custom encrypted transport and [SECURITY.md](SECURITY.md) for reporting concerns.

The About dialog uses a bundled Moon image and local project links. When connected, it checks the public `patches-bundle.json` on raw.githubusercontent.com for a newer Moon version, with a five-minute cache; GitHub receives the network request and IP address. It does not fetch Morphe branding or donation links. Tapping a project or attribution link opens the external browser.
