# Building Moon Patches

Use JDK 17, the included Gradle wrapper, Android SDK platform 36 and build tools 36.0.0. Do not commit local SDK paths or credentials. `ANDROID_HOME` must point to your SDK.

The settings plugin and some upstream artifacts use GitHub Packages. Provide `GITHUB_ACTOR` and a `GITHUB_TOKEN` with the package read access required by the upstream setup, or use the local `gpr.user`/`gpr.key` Gradle properties. Never place a token in the repository. Refer to [upstream environment setup](https://github.com/MorpheApp/morphe-patcher/blob/main/docs/2_1_setup.md).

To build the same source dependencies used for this release, clone these sibling directories and check out their pinned commits:

| Directory | Repository | Commit |
| --- | --- | --- |
| `morphe-patcher` | https://github.com/MorpheApp/morphe-patcher | `5eacde46237f2fe657eb9bfbe90d2528d248a336` |
| `morphe-patches-library` | https://github.com/MorpheApp/morphe-patches-library | `941a18a5992d5c929dc3f438cb05c8944091c8e3` |

`settings.gradle.kts` automatically substitutes these composite builds when present. Other versions are pinned in `gradle/libs.versions.toml`, settings and the module build files.

```sh
./gradlew :room-core:test buildAndroid generatePatchesList --no-daemon --max-workers=2 --no-parallel
```

On Windows use `gradlew.bat`. The final bundle is `patches/build/libs/patches-<version>.mpp`. Wait for the complete Android build: a jar-only partial file is not an installable patch bundle. The archive must contain `classes.dex` and generated Android extensions.

Optional bounded real-relay test (creates and closes its own ephemeral test sessions):

```sh
./gradlew :room-core:liveLobbyProbe
```

To validate against an original APK you legally obtained, including Moon resources and DEX verification:

```sh
./gradlew :patches:validateRooms -ProomsApk=/absolute/path/youtube.apk
```

This does not sign, install or publish an APK. `roomsOutput` optionally writes an unsigned validation APK locally; do not redistribute it. Physical tests should use Morphe to patch the original APK.

Committed branding assets are sufficient to build. To regenerate their deterministic density exports, install `sharp` for `tools/branding/render.cjs` and run it from the repository root. The cleaned texture and outlined wordmark masters are included; no image-generation service or proprietary font is required at build time.

## Releases

Update `version` in `gradle.properties`, CHANGELOG and validation notes. Run tests and the complete build, generate the patch list, then `python tools/release_metadata.py <bundle>`. Commit the metadata and tag that exact source as `v<version>`. The GitHub release contains the `.mpp`, matching `patches-bundle.json`, SHA256 checksums and a corresponding source archive. The included CI workflow builds/test-checks changes; publication remains an explicit maintainer action.
