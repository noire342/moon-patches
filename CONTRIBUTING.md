# Contributing

Open an issue describing the behavior, exact bundle/Morphe/YouTube/Android versions and reproduction steps. Share sanitized logs only; remove room codes, private keys, invitations, account data and credentials.

Build using [docs/build.md](docs/build.md). Keep `room-core` independent of Android. Player adapters must fail closed when fingerprints are ambiguous. Preserve native player behavior outside a room, existing launcher aliases and upstream license notices. Add tests for protocol changes, authorization, replay handling and synchronization ordering. Run the relevant unit tests and buildAndroid before proposing a release. Device or real-relay checks must be clearly distinguished from simulated tests.

Pull requests should explain the concrete problem, resulting behavior and validation. Contributions are accepted under the project GPL-3.0-only terms; third-party material requires documented provenance and compatible licensing. Do not include stock or patched proprietary APKs, signing keys, access tokens, generated build folders or private Nyanime source.
