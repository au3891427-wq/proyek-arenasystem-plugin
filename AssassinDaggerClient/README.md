# Assassin Dagger Client

Fabric client-side module for ArenaSystem, targeting Minecraft 26.3 and Java 25.

## Current scope

- Fabric client entrypoint.
- Stable-channel update manifest check at startup.
- HTTPS-only GitHub release downloads.
- SHA-256 verification and bounded download sizes.
- Mod discovery by Fabric Mod ID rather than an assumed JAR filename.
- Versioned local JAR installation (`ArenaSystem_Client-<version>.jar`).
- Windows shutdown helper handoff; the helper waits for Minecraft to exit before replacing the JAR.
- Backup and rollback behavior in the standalone helper.

The client mod currently contains the updater foundation only. Dagger rendering, animations,
input handling, and Paper plugin networking are not implemented in this module yet.

## Build

Requires JDK 25 and Gradle 9.8.0.

```powershell
gradle --no-daemon clean build
```

Output: `build/libs/arenasystem-assassin-dagger-client.jar`.

## Automatic stable releases and updates

The client checks the dedicated public file `assassin-client-update-manifest.json` on GitHub in
the background every time Minecraft starts. When a newer compatible stable version is found,
it downloads the client JAR and Windows helper from that release, verifies their SHA-256 hashes,
and stages the update. On Windows, the helper installs the JAR after Minecraft fully exits and
keeps a verified backup for rollback.

Stable release publication is automated:

- A push to `main` that changes `AssassinDaggerClient/**`, `AssassinDaggerUpdater/**`, or
  this release workflow builds and validates the client and helper.
- The release version comes from `mod_version` in `AssassinDaggerClient/gradle.properties`.
- If `assassin-client-v<version>` does not already exist, GitHub Actions creates the stable
  release, uploads the client JAR, updater helper, and generated `update-manifest.json`, then
  commits the same manifest to the repository root as `assassin-client-update-manifest.json`.
- A version that already has a release is never overwritten. Bump `mod_version` for each
  new client release.
- Pushing a tag such as `assassin-client-v0.1.1` also publishes that exact version.
- Pull requests and manual workflow runs default to dry-run and cannot publish accidentally.

The client reads only the dedicated Assassin manifest, not GitHub's generic latest-release
endpoint. This prevents a stable Paper plugin release from accidentally being treated as a
Fabric client update. The existing `plugin` prerelease is also ignored.

**Scope:** this updater replaces only the Fabric client JAR on Windows, when Minecraft exits.
It does not update the Paper server plugin. Other operating systems download and verify an update
but require the player to install the staged JAR manually.

The manifest must include:

- `schemaVersion`
- `modId`
- `version`
- `minecraft`
- `loader`
- `channel`
- `downloadUrl`
- `sha256`
- `helperSha256`
- `loaderVersion` (minimum Fabric Loader version required by the release)

Pull requests and manual workflow dispatch run in dry-run mode: they build the client and helper,
derive Minecraft and Loader versions from `gradle.properties`, validate the embedded
`fabric.mod.json` metadata and generated manifest, and upload the three assets as a workflow
artifact. Only pushing an `assassin-client-vX.Y.Z` tag can publish a GitHub Release. The
preparation job has read-only repository permissions; write permission exists only in the
tag-triggered publish job.

## Windows install flow

1. Client checks the latest stable release in the background.
2. It verifies mod ID, loader, exact Minecraft version, HTTPS GitHub URL, and SHA-256.
3. It resolves the installed JAR path from the Fabric mod container found by `modId`; no local JAR filename is hard-coded.
4. It stages the release as `mods/ArenaSystem_Client-<version>.jar.pending`.
5. It downloads and verifies the matching helper from the same release tag into `.arenasystem/updater/`.
6. A file lock in `mods/` prevents separate Minecraft instances from staging overlapping updates.
7. During JVM shutdown, it starts the helper with the current Minecraft PID, using `javaw.exe`
   or the `java.exe` fallback from the active Java runtime.
8. The helper waits for Minecraft to exit, backs up the old JAR, installs and verifies the new
   versioned JAR, then removes the old JAR. If installation fails, the old JAR remains available
   and the new destination is quarantined or removed.

The updater does not install Paper plugin JARs and does not execute content from the manifest.

## Limitations before production

- First release must be published before automatic updates can work.
- The published GitHub release asset keeps its stable download name, while the installed local JAR is named `ArenaSystem_Client-<version>.jar`.
- Automatic replacement is currently Windows-only. Other operating systems retain the verified
  staged update without replacing the active JAR.
- SHA-256 protects against accidental corruption and mismatched assets. It does not provide
  independent publisher authenticity if the GitHub repository/release itself is compromised.
- Existing v0.1.0 clients use their older fixed staging name for the first automatic update; the new Mod-ID-based naming flow applies to subsequent updates. To use a versioned filename immediately, reinstall the new release once using its versioned local filename.
- End-to-end testing on a real Windows Minecraft installation is still required.
