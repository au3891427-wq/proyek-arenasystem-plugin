# Assassin Dagger Windows Updater Helper

This is a narrowly scoped helper launched by the Fabric client mod during JVM shutdown.

## Contract

Arguments:

`<minecraft-pid> <target-jar> <staged-jar> <expected-sha256>`

- Waits up to 180 seconds for the Minecraft process to exit.
- Only replaces `arenasystem-assassin-dagger-client.jar`.
- Requires target and staged JAR to be in the same directory named `mods`.
- Requires staged filename `arenasystem-assassin-dagger-client.jar.pending`.
- Verifies SHA-256 before and after installation.
- Keeps a `.backup` file and restores it if installation fails.
- Does not download files, execute scripts, or accept URLs.

The Fabric mod must validate the manifest and download over HTTPS from the project's
GitHub Releases before invoking this helper. It should extract this helper JAR into
the game directory's `.arenasystem/updater` folder and launch it with
`javaw -jar` from a JVM shutdown hook.

## Build

Requires JDK 25 and Gradle 8.14 or newer.

```powershell
gradle clean build
```

The runnable JAR is written to `build/libs/assassin-dagger-updater-0.1.0.jar`.

This helper is source code only until it is integrated into the Fabric mod's
download/staging lifecycle and exercised on Windows.
