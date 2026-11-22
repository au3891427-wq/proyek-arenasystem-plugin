# ArenaSystem Client Windows Updater

This helper is launched by the ArenaSystem Fabric client during JVM shutdown.

Arguments: `<minecraft-pid> <current-mod-jar> <new-mod-jar.pending> <expected-sha256>`.

- Waits for Minecraft to exit, then acquires the shared updater lock.
- Accepts any installed client JAR filename; the client resolves its active JAR by Fabric Mod ID.
- Derives the destination by removing `.pending` from the staged filename.
- Supports in-place and versioned replacement, e.g. `ArenaSystem_Client-0.1.2.jar`.
- Requires all three JAR paths to share one real directory named `mods`.
- Retains the historical `.arenasystem-assassin-dagger-update.lock` filename so old and new
  updater versions serialize against the same lock.
- Verifies SHA-256 before and after installation, retains a verified backup, and removes the old
  JAR only after a new versioned JAR is installed and verified.
- Does not download files, execute scripts, or accept URLs.

The client validates the manifest and downloads over HTTPS from GitHub Releases before invoking
this helper. It is extracted into `.arenasystem/updater/`.

Build with JDK 25 and Gradle 9.8.0:

```powershell
gradle --no-daemon clean build
```

Output: `build/libs/ArenaSystem_Client_Updater-0.1.0.jar`.

Windows CI tests installation, checksum rejection, missing-stage handling, process timeout,
locked backup, rollback, and concurrent lock serialization. A real Minecraft shutdown-hook test
is still required.
