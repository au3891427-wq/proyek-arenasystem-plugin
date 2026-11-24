# ArenaSystem Client (Fabric)

Client companion for ArenaSystem Paper, targeting Minecraft 26.3, Fabric Loader 0.20.1,
Fabric API 0.166.2+26.3, and Java 25.

## Identity

- Mod ID: `arstm_client0qb201`
- Legacy alias: `arenasystem_assassin_dagger`
- Version: **0.2.5**
- Artifact: `ArenaSystem_Client-0.2.5.jar`

Install only one ArenaSystem client JAR in the mods directory. Do not keep old and new JARs
side by side.

## Features

- Paper companion handshake on `arenasystem:mod_handshake`, preserving Handshake Protocol v2.
- Startup update check with HTTPS GitHub release downloads and SHA-256 verification.
- Three remappable gameplay key mappings: primary skill, secondary skill, and held class-skill item.
- Client-side checks for Paper PDC markers before using a held class-skill item.
- Embedded `arenasystem:assassin_dagger` item definition, model, and animated texture.
- Receives the server-authoritative Assassin off-hand cooldown and draws its yellow recharge bar
  **above the crosshair**, separate from the vanilla main-hand attack indicator below it.

The Paper plugin remains authoritative for class selection, skill availability, target validation,
cooldowns, damage, and successful-hit feedback. The off-hand cooldown display starts only after the
server sends its cooldown payload; the client does not decide whether a sweep or hit is valid.

## Default controls

Defaults are starting bindings only. Players can remap them in **Options → Controls → Key Binds**.

| Default key | Action |
|---|---|
| G | Primary class skill |
| H | Secondary class skill |
| J | Use a held class-skill item |

Assassin's off-hand blade has no standalone key mapping. It activates only when the Paper server
confirms a real main-hand sweep and both hands contain valid Assassin blades.

## Embedded dagger resources

The dagger resources live under `src/main/resources/assets/arenasystem/`; they are part of the mod
JAR and are not a separately selectable resource pack.

## Build

Requires JDK 25 and Gradle 9.8.0.

Run from this directory:

```bash
gradle --no-daemon clean build
```

Output: `build/libs/ArenaSystem_Client-0.2.5.jar`.

The CI workflow compiles the mod and runs the existing standalone handshake and class-skill input
protocol tests. These tests do not replace a live Paper/Fabric integration test.

## Updates and compatibility

Handshake Protocol v2 and the updater implementation remain unchanged. The updater still reads
the repository's existing manifest and release layout. Automatic replacement is Windows-only;
other operating systems require manual installation of the verified staged JAR.

The updater does not update the Paper server plugin.


### Assassin off-hand input

The Fabric client detects a right-click press while the crosshair targets air or a player entity and sends action `3` through the existing `arenasystem:skill_input` protocol. It deliberately ignores block targets so vanilla chest, door and other block interactions remain unchanged. The Paper plugin remains authoritative for every validation, strike, damage and cooldown decision.
