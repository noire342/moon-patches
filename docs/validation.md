# Validation record

Version 1.47.1, checked on 2026-10-12. The integration targets regular YouTube playback and is experimental.

Automated and SDK evidence:

- 28 rooms JVM tests passed (26 protocol/controller tests and 2 close-drain regressions), covering signature vectors, malformed messages, host admission, replay handling, stale state, invitation parsing, lobby transitions, guest permissions, native control ordering and relay acknowledgements. Eight inherited patch regression tests also passed.
- Two project-owned identities connected through real public relays with simulated players: lobby admission without a video, host selection, guest play/pause/seek with default shared controls and host closure passed. This is not two-phone playback proof. A lost host-close event found during validation led to a bounded acknowledgement drain before socket shutdown and the two added regression tests.
- Stock YouTube 21.20.405 patching and Moon resource compilation passed. Cross-DEX hierarchy verification passed for 58,395 classes across 12 DEX files. Earlier development also validated 21.16.256; the final Moon UI was tested on 21.20.405.

Physical-device evidence (Samsung SM-F741B, USB, YouTube 21.20.405):

- Morphe imported Moon Patches 1.47.1 and repatched the saved original APK successfully, preserving app data. The bundle catalogue contained 171 patches for 3 apps.
- The room button appeared beside Search on Home. Create/Join panes, keyboard resizing, invalid invitation handling, room creation without a video, participant display and default shared controls were checked. The native “Scegli cosa guardare” action opened YouTube Search. Connection/status text displayed correctly.
- The final About build was repatched and installed through Morphe at 22:13 on 2026-10-12. About showed the bundled Moon logo, gradient name, version 1.47.1 and own project links. Credits retained Moon, Morphe, RVX, ReVanced and Vanced. Opening the Morphe license entry displayed the original NOTICE and complete GPL text. Moon appeared in the app icon/name controls, and selecting the Moon header rendered the gradient wordmark with the moon on its right on Home.
- A Samsung SM-F741B running patched YouTube 21.20.405 successfully created rooms; a desktop peer exchanged controls with the phone. The later toolbar appeared beside Search and the default shared-controls switch was enabled. No second physical phone was available.
- A real feed-preview lobby defect was observed and corrected by excluding inline/dismissed/Shorts player states. A complete final playback/buffering recheck on two devices is still outstanding.

Remaining coverage includes synchronization on two physical phones under different network/buffering conditions, a sponsored segment during/after a session, OEM launcher masks, accessibility at very large font scales and casting/live/Shorts behavior (unsupported room modes).

Build success, static verification, real-relay tests and physical-device checks are separate forms of evidence. None establishes a guarantee that every app version or network behaves identically.
