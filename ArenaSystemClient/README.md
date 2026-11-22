# ArenaSystem Client (Fabric)

Fabric client module for ArenaSystem, targeting Minecraft 26.3 and Java 25.

## Identity

- Mod ID: `arstm_client0qb201`
- Legacy alias: `arenasystem_assassin_dagger` (provided for old dependency compatibility)
- Display name: **ArenaSystem Client**

Install only one ArenaSystem client JAR in the `mods` directory. Do not keep old and new JARs side by side.

## Build

Requires JDK 25 and Gradle 9.8.0.

```powershell
gradle --no-daemon clean build
```

Output: `build/libs/ArenaSystem_Client-<version>.jar`.

## Updates and compatibility

New clients read `arstm-client0qb201-update-manifest.json`. Installed v0.1.0/v0.1.1 clients
continue reading `assassin-client-update-manifest.json`. Each new release therefore publishes
identical verified client bytes under both `ArenaSystem_Client-<version>.jar` and the old
`arenasystem-assassin-dagger-client.jar` asset name. The legacy manifest retains the old Mod ID
and URL. The new JAR declares the old ID via Fabric's `provides` metadata.

The helper is published under both its new and old asset names. The release tag prefix
`assassin-client-v` is retained only because old updaters validate that URL layout.

The client currently contains the updater foundation only; Dagger rendering, animations, input
handling, and Paper plugin networking are not implemented here. Automatic replacement is
Windows-only; other OSes require manual installation of the verified staged JAR.

## Windows update flow

The client verifies Mod ID, loader, Minecraft version, HTTPS GitHub URL, and SHA-256; discovers
its active JAR by Mod ID; then stages `mods/ArenaSystem_Client-<version>.jar.pending`.
The helper waits for Minecraft to exit, backs up the old JAR, verifies the new JAR, and only then
removes the old one. The historical updater lock filename remains in use so old and new updater
versions coordinate through one lock.

GitHub Actions builds and tests the client and helper. A real Minecraft Windows shutdown-hook test
is still required before automatic replacement can be considered fully production-validated.
