# Licensing and credits

Moon Patches is a modified distribution of [Morphe Patches](https://github.com/MorpheApp/morphe-patches), based on commit `e5464395441e916b643af4e2175c4e253e6df487` (base release 1.47.0). Its earlier ReVanced/ReVanced Extended origins remain documented in the inherited code and notices. No Nyanime application source is distributed in this repository; the new standalone rooms core uses its own namespace for future integration.

The project is licensed under **GNU GPL version 3 only**. The complete text is in [LICENSE](LICENSE). The dated [modified-distribution notice](MODIFICATIONS.md) identifies this fork and its changes. The dated [modified-distribution notice](MODIFICATIONS.md) identifies this fork and its changes. The dated [modified-distribution notice](MODIFICATIONS.md) identifies this fork and its changes. The original [NOTICE](NOTICE) is preserved verbatim, including the Section 7 attribution, modified-origin and trademark terms. These notices must accompany redistributed source and binaries. Corresponding source is provided by the release tag, repository and release source archive, including build configuration and pinned dependency information.

Original Morphe branding is separately copyrighted and requires permission under the [upstream branding guidelines](https://github.com/MorpheApp/morphe-branding). Moon does not redistribute those logos, header graphics or startup animations. [The replacement manifest](docs/branding/replaced-upstream-assets.json) records changed files. Resource identifiers and launcher aliases remain to preserve update compatibility. Morphe, YouTube and other names are used to describe origin or compatibility; no endorsement or trademark rights are granted.

New Moon artwork, layout assets and code are under GPL-3.0-only. The Moon illustration was supplied by the project owner; its background and edge cleanup used image generation, followed by a circular clip and native asset rendering. The wordmark is a static outlined word rendered from Segoe UI Bold, not a redistributed font or reusable glyph set. See [Microsoft's font output guidance](https://learn.microsoft.com/en-us/typography/fonts/font-faq). No font files are included.

The rooms runtime additionally includes:

| Component | Version | License |
| --- | --- | --- |
| Bouncy Castle bcprov-jdk18on | 1.85 | MIT |
| Java-WebSocket | 1.6.0 | MIT |
| Gson | 2.14.0 | Apache-2.0 |
| SLF4J | 2.0.17 | MIT |
| JUnit Jupiter (tests only) | 5.10.1 | EPL-2.0 |

Copies and links are in [docs/nya-rooms-dependencies.md](docs/nya-rooms-dependencies.md) and [docs/rooms/licenses](docs/rooms/licenses). BIP-340 test vectors retain their Bitcoin BIPs provenance. The inherited patch dependencies, library notices and Tabler icon licenses remain in the corresponding source/resources and the app's license dialog. Dependencies retain their own terms; GPL licensing of this fork does not relicense them.
