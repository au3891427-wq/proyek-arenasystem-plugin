# Assassin Dagger Client

Fabric client-side module for ArenaSystem, targeting Minecraft 26.3 and Java 25.

## Current scope

- Fabric client entrypoint.
- Stable-channel update manifest check at startup.
- HTTPS-only GitHub release downloads.
- SHA-256 verification and bounded download sizes.
- Staging beside the installed mod JAR.
- Windows shutdown helper handoff; the helper waits for Minecraft to exit before replacing the JAR.
- Backup and rollback behavior in the standalone helper.

The client mod currently contains the updater foundation only. Dagger rendering, animations,
input handling, and Paper plugin networking are not implemented in this module yet.

## Build

Requires JDK 25 and Gradle 9.7.1.

```powershell
gradle --no-daemon clean build
```

Output: `build/libs/arenasystem-assassin-dagger-client.jar`.

## Release

Push a tag such as `assassin-client-v0.1.0`. GitHub Actions builds both the client mod and
Windows helper, calculates SHA-256 hashes, creates `update-manifest.json`, and publishes
all three release assets. Only stable releases are checked by the client.

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

## Windows install flow

1. Client checks the latest stable release in the background.
2. It verifies mod ID, loader, exact Minecraft version, HTTPS GitHub URL, and SHA-256.
3. It downloads the mod JAR to `mods/arenasystem-assassin-dagger-client.jar.pending`.
4. It downloads and verifies the standalone helper into `.arenasystem/updater/`.
5. During JVM shutdown, it starts the helper with the current Minecraft PID.
6. The helper waits for Minecraft to exit, backs up the current JAR, installs the staged JAR,
   verifies the installed hash, and restores the backup if verification fails.

The updater does not install Paper plugin JARs and does not execute content from the manifest.

## Limitations before production

- First release must be published before automatic updates can work.
- The mod JAR filename must remain exactly `arenasystem-assassin-dagger-client.jar`.
- Automatic replacement is currently Windows-only. Other operating systems retain the verified
  staged update without replacing the active JAR.
- SHA-256 protects against accidental corruption and mismatched assets. It does not provide
  independent publisher authenticity if the GitHub repository/release itself is compromised.
- End-to-end testing on a real Windows Minecraft installation is still required.
