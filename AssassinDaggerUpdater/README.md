# Assassin Dagger Windows Updater Helper

This is a narrowly scoped helper launched by the Fabric client mod during JVM shutdown.

## Contract

Arguments:

`<minecraft-pid> <target-jar> <staged-jar> <expected-sha256>`

- Waits up to 180 seconds for the Minecraft process to exit and then acquires the shared updater file lock.
- Only replaces `arenasystem-assassin-dagger-client.jar`.
- Requires target and staged JAR to be in the same directory named `mods`.
- Requires staged filename `arenasystem-assassin-dagger-client.jar.pending`.
- Uses the same `.arenasystem-assassin-dagger-update.lock` file as the client to serialize separate game instances.
- Verifies SHA-256 before and after installation.
- Keeps a `.backup` file and restores it if installation fails.
- Does not download files, execute scripts, or accept URLs.

The Fabric mod must validate the manifest and download over HTTPS from the project's
GitHub Releases before invoking this helper. It should extract this helper JAR into
the game directory's `.arenasystem/updater` folder and launch it with
`javaw -jar` from a JVM shutdown hook.

## Build

Requires JDK 25 and Gradle 9.8.0 in CI.

```powershell
gradle clean build
```

The runnable JAR is written to `build/libs/assassin-dagger-updater-0.1.0.jar`.

The Windows CI workflow builds this helper and exercises installation, checksum rejection,
missing-stage handling, process timeout, locked backup, and rollback after a locked staged-file move.
An end-to-end test with an actual Minecraft shutdown hook is still required.
