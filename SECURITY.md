# Security

Rooms are experimental and have not received an independent cryptographic audit. Read [PRIVACY.md](PRIVACY.md) and the [protocol notes](docs/rooms/protocol.md) before relying on them.

Report vulnerabilities through [GitHub private vulnerability reporting](https://github.com/noire342/moon-patches/security/advisories/new). Avoid posting exploit details, room invitations or secrets in public issues. If private reporting is unavailable, open an issue asking the maintainer for a private reporting channel without disclosing the vulnerability.

Include the affected release, component, reproduction details and security impact. The current published release is the supported version; no response-time guarantee is made. Sensitive issues include admission bypass, signature/envelope validation, replay handling, unsafe player injection and SponsorBlock activity during a room.
