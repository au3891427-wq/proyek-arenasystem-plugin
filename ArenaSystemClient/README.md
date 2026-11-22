# ArenaSystem Client (Fabric)

Fabric client module for ArenaSystem, targeting Minecraft 26.3 and Java 25.

## Identity

- Mod ID: arstm_client0qb201
- Legacy alias: arenasystem_assassin_dagger (provided for old dependency compatibility)
- Display name: **ArenaSystem Client**
- Client version: **0.1.5**

Install only one ArenaSystem client JAR in the mods directory. Do not keep old and new JARs side by side.

## Features

- Paper companion handshake on arenasystem:mod_handshake, implementing Handshake Protocol v2.
- Validates the 25-byte protocol header, message type, protocol version, session UUID, and exact message body sizes.
- Performs the complete SERVER_HELLO → CLIENT_HELLO → AUTH_CHALLENGE → AUTHENTICATE → SERVER_CHALLENGE → CONNECTION_CHECK → CONNECTION_ACK exchange.
- Uses the server-compatible HMAC-SHA256 proof format and constant-time proof comparisons.
- Startup update check with HTTPS GitHub release downloads and SHA-256 verification.
- Resolves the installed client JAR by Mod ID, then stages ArenaSystem_Client-<version>.jar.
- Windows shutdown helper with verified backup and rollback.

The client module currently provides the handshake and updater foundation. Dagger rendering,
animations, and input handling are not implemented in this module yet.

## Build

Requires JDK 25 and Gradle 9.8.0.

Run: gradle --no-daemon clean build

Output: build/libs/ArenaSystem_Client-<version>.jar.

Fabric API is a required runtime dependency. The build compiles against Fabric API
0.166.2+26.3, and the mod declares fabric-api as a required dependency.

The CI workflow also compiles and runs the standalone binary protocol tests, including fixed
HMAC-SHA256 test vectors for all four proof purposes. These tests validate the binary protocol
implementation; they do not replace a live Paper/Fabric integration test.

## Updates and old-client compatibility

New clients read arstm-client0qb201-update-manifest.json. Installed v0.1.0/v0.1.1 clients
continue reading assassin-client-update-manifest.json. Each new release publishes identical
verified client bytes under both ArenaSystem_Client-<version>.jar and the old
arenasystem-assassin-dagger-client.jar asset name. The legacy manifest retains the old Mod ID
and URL. The new JAR declares the old ID via Fabric's provides metadata.

The helper is published under both its new and old asset names. The release tag prefix
assassin-client-v is retained only because old updaters validate that URL layout.

Automatic replacement is Windows-only; other operating systems require manual installation of
the verified staged JAR. The updater does not update the Paper server plugin.

## Windows update flow

The client checks its manifest at startup, verifies Mod ID, loader, Minecraft version, HTTPS URL,
and SHA-256, then discovers its installed JAR by Mod ID. It stages
mods/ArenaSystem_Client-<version>.jar.pending. The helper waits for Minecraft to exit, backs up
the old JAR, verifies the new JAR, and only then removes the old one.
