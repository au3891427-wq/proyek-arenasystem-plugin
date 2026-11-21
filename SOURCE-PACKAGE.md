# ArenaSystem Fabric Client Mod 0.1.1 — Source Package

This archive contains the complete source projects used for the ArenaSystem Fabric companion client:

- `AssassinDaggerClient/`: the actual Fabric client mod, including the handshake client, updater manifest/download validation, and update staging logic.
- `AssassinDaggerUpdater/`: the standalone Windows updater helper launched by the mod during shutdown. It is not itself a Fabric mod and must not be copied into `.minecraft/mods`.

## Compatibility

- Minecraft: 26.3
- Fabric Loader: 0.20.1 or newer
- Fabric API: 0.166.0+26.3 or newer
- Java: 25+
- Gradle: 9.8.0 (the CI build uses this exact Gradle version)

## Build the Fabric mod

Open PowerShell in `AssassinDaggerClient`:

```powershell
gradle --no-daemon clean build
```

The mod JAR is written to:

```
AssassinDaggerClient/build/libs/arenaSystem_client-0.1.1.jar
```

## Build the Windows updater helper

Open PowerShell in `AssassinDaggerUpdater`:

```powershell
gradle --no-daemon clean build
```

The helper JAR is written to:

```
AssassinDaggerUpdater/build/libs/assassin-dagger-updater-0.1.0.jar
```

The helper belongs in the game's private `.arenasystem/updater` directory at runtime. The client mod extracts it there when an update is staged. Do not place the helper in `.minecraft/mods`.

## Handshake protocol

The client registers `arenasystem:mod_handshake`. The server sends a 16-byte UUID nonce; the mod replies with a 20-byte payload consisting of a big-endian 32-bit protocol version (`1`) and that same UUID nonce.

This is a client presence/protocol compatibility handshake, not cryptographic attestation.

## Release status

This is source for the 0.1.1 development branch and paired ArenaSystem server handshake work. The branch is not the same thing as a published stable GitHub Release. The public update manifest may point to an older stable version until the paired changes are released.
