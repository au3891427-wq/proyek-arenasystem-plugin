# Assassin Dagger Client

Fabric client-side module for ArenaSystem, targeting Minecraft 26.3 and Java 25.

## Current scope

- Fabric client entrypoint.
- Challenge-response presence handshake with the ArenaSystem Paper plugin on
  `arenasystem:mod_handshake` (protocol 2).
- Stable-channel update manifest check at startup.
- HTTPS-only GitHub release downloads.
- SHA-256 verification and bounded download sizes.
- Staging beside the installed mod JAR.
- Windows shutdown helper handoff; the helper waits for Minecraft to exit before replacing the JAR.
- Backup and rollback behavior in the standalone helper.

A built-in, always-enabled resource pack is bundled under `resourcepacks/assassin_dagger/`.
It contains the item definition, model, four-frame animated pixel-art texture, and keybind
translations for the server item model `arenasystem:assassin_dagger`. No external resource-pack
host is required for these client assets.

The client also registers a configurable `Twin Blade: Off-hand Strike` key mapping (default
`R`, editable in Options > Controls > Key Binds). It invokes the same main-hand use
interaction already handled by the Paper plugin when both hands hold golden swords.

The Paper plugin remains authoritative for Assassin class validation, target checks, range,
damage, cooldowns, and the off-hand swing. Vanilla right-click remains supported; the custom
keybind sends the same main-hand use interaction and does not implement a second attack system.
The handshake verifies mod presence and protocol compatibility, not cryptographic
attestation: a modified client could imitate the response.

## Build

Requires JDK 25, Gradle 9.8.0, Fabric Loader 0.20.1, and Fabric API
`0.166.0+26.3`.

Minecraft 26.3 uses the non-remapping Fabric Loom plugin, so Fabric dependencies
are declared with Gradle's standard `implementation` configuration.

```powershell
gradle --no-daemon clean build
```

Output: `build/libs/arenaSystem_client-<version>.jar`.

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
3. It downloads the versioned release asset to a `.pending` file beside the currently installed client JAR.
4. It downloads and verifies the matching helper from the same release tag into `.arenasystem/updater/`.
5. A file lock in `mods/` prevents separate Minecraft instances from staging overlapping updates.
6. During JVM shutdown, it starts the helper with the current Minecraft PID, using `javaw.exe`
   or the `java.exe` fallback from the active Java runtime.
7. The helper waits for Minecraft to exit and for the update lock to become available, backs up
   the current JAR, installs the staged JAR, verifies the installed hash, and restores the backup
   if installation fails.

The updater does not install Paper plugin JARs and does not execute content from the manifest.

## Limitations before production

- First release must be published before automatic updates can work.
- Release assets use the versioned name `arenaSystem_client-<version>.jar`; the updater detects the installed client JAR by Fabric mod ID and replaces it in place.
- Automatic replacement is currently Windows-only. Other operating systems retain the verified
  staged update without replacing the active JAR.
- SHA-256 protects against accidental corruption and mismatched assets. It does not provide
  independent publisher authenticity if the GitHub repository/release itself is compromised.
- End-to-end testing on a real Windows Minecraft installation is still required.
