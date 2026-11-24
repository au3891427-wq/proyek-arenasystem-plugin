# ArenaSystem Client (Fabric)

Client companion for ArenaSystem Paper, targeting Minecraft 26.3, Fabric Loader 0.20.1,
Fabric API 0.166.2+26.3, and Java 25.

## Identity

- Mod ID: `arstm_client0qb201`
- Legacy alias: `arenasystem_assassin_dagger`
- Version: **0.2.7**
- Artifact: `ArenaSystem_Client-0.2.7.jar`

Install only one ArenaSystem client JAR in the mods directory. Do not keep old and new JARs
side by side.

## Features

- Paper companion handshake on `arenasystem:mod_handshake`, preserving Handshake Protocol v2.
- Startup update check with HTTPS GitHub release downloads and SHA-256 verification.
- Client-side discrete input detection for primary and secondary class skills.
- Client-side checks for Paper PDC markers before using a held class-skill item.
- Right-mouse click mapping for the Assassin off-hand attack; held-button state does not repeat it.
- Embedded `arenasystem:assassin_dagger` item definition, model, and animated texture.
- Receives the server-authoritative Assassin off-hand cooldown and draws its yellow recharge bar
  **above the crosshair**, separate from the vanilla main-hand attack indicator below it.

The Fabric client detects physical key/mouse presses and sends explicit action packets. The Paper
plugin remains authoritative for class selection, skill availability, target validation, cooldowns,
damage, and successful-hit feedback. The client does not decide whether a skill or hit is valid.

## Default controls

Defaults are starting bindings. Players can remap them in **Options → Controls → Key Binds**.

| Default input | Action |
|---|---|
| G | Primary class skill |
| H | Secondary class skill |
| J | Use a held class-skill item |
| Right Mouse Button | Assassin off-hand attack, when both hands hold valid Assassin blades |

Primary/secondary skills and the off-hand attack are detected on the Fabric client with queued
press events. Holding a key or mouse button does not create repeated input events. The Paper server
receives the action packet and executes the validated ability.

The off-hand input requires the ArenaSystem dagger marker in both hands. Vanilla main-hand
item/entity use is suppressed for that paired-blade action to avoid sending a second interaction.
Block-use callbacks are not intercepted.

## Embedded dagger resources

The dagger resources live under `src/main/resources/assets/arenasystem/`; they are part of the mod
JAR and are not a separately selectable resource pack.

## Build

Requires JDK 25 and Gradle 9.8.0.

Run from this directory:

```bash
gradle --no-daemon clean build
```

Output: `build/libs/ArenaSystem_Client-0.2.7.jar`.

The CI workflow compiles the mod and runs the existing standalone handshake and class-skill input
protocol tests. These tests do not replace a live Paper/Fabric integration test.

## Updates and compatibility

Handshake Protocol v2 and the updater implementation remain unchanged. The updater still reads
the repository's existing manifest and release layout. Automatic replacement is Windows-only;
other operating systems require manual installation of the verified staged JAR.

The updater does not update the Paper server plugin.
