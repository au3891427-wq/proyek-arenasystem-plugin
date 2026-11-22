# Assassin Dagger Windows Updater Helper

This is a narrowly scoped helper launched by the Fabric client mod during JVM shutdown.

## Contract

Arguments:

`<minecraft-pid> <current-mod-jar> <new-mod-jar.pending> <expected-sha256>`

- Waits up to 180 seconds for the Minecraft process to exit and then acquires the shared updater file lock.
- Accepts any current mod JAR filename; the Fabric client resolves the current JAR by Mod ID.
- Derives the destination filename by removing `.pending` from the staged filename.
- Supports both in-place replacement (same current/destination filename) and versioned replacement (for example, `ArenaSystem_Client-0.1.1.jar`).
- Requires current, destination, and staged JAR paths to share one directory named `mods`.
- Requires the staged filename to end in `.jar.pending`.
- Uses the same `.arenasystem-assassin-dagger-update.lock` file as the client to serialize separate game instances.
- Verifies SHA-256 before and after installation.
- Keeps a verified backup of the old JAR. For versioned replacement, the old JAR is removed only after the new JAR passes SHA-256 verification; failures leave the old JAR intact.
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
missing-stage handling, process timeout, locked backup, rollback after a locked staged-file move,
and serialization when two updater processes contend for the shared file lock. An end-to-end test
with an actual Minecraft shutdown hook is still required.
