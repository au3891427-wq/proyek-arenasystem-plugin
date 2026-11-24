# ArenaSystem Client (Fabric)

Client companion for ArenaSystem Paper, targeting Minecraft 26.3, Fabric Loader 0.20.1,
Fabric API 0.166.2+26.3, and Java 25.

## Identity

- Mod ID: `arstm_client0qb201`
- Legacy alias: `arenasystem_assassin_dagger`
- Version: **0.2.9**
- Artifact: `ArenaSystem_Client-0.2.9.jar`

Install only one ArenaSystem client JAR in the mods directory. Do not keep old and new JARs
side by side.

## Features

- Paper companion handshake on `arenasystem:mod_handshake`, preserving Handshake Protocol v2.
- Startup update check with HTTPS GitHub release downloads and SHA-256 verification.
- Client-side chord detection for primary/secondary skills, held class-skill items, Sniper Aim Lock,
  and Assassin off-hand attack.
- Client-side checks for Paper PDC markers before using a held class-skill item or sending an
  Assassin off-hand action.
- Embedded `arenasystem:assassin_dagger` item definition, model, and animated texture.
- Receives the server-authoritative Assassin off-hand cooldown and draws its yellow recharge bar
  **above the crosshair**, separate from the vanilla main-hand attack indicator below it.

The Fabric client detects key/mouse presses and sends explicit action packets. The Paper plugin
remains authoritative for class selection, skill availability, target validation, cooldowns, damage,
and successful-hit feedback. The client does not decide whether a skill or hit is valid.

## Default controls

Defaults are starting bindings. Players can remap each base key/button in **Options → Controls → Key
Binds**. The required modifier for each ArenaSystem action is fixed; the Controls descriptions show
which modifier must be held.

| Default input | Action |
|---|---|
| Shift + G | Primary class skill |
| Shift + H | Secondary class skill |
| Shift + J | Use a held class-skill item |
| Alt + Q | Toggle Sniper Aim Lock |
| Ctrl + Right Mouse Button | Assassin off-hand attack, when both hands hold valid Assassin blades |

Either left or right Shift, Alt, or Ctrl is accepted. A chord requires its exact modifier set; pressing
additional modifier keys at the same time will not activate it.

The client uses discrete key/button press events. Holding a base key or mouse button does not create
repeated action packets. The Paper server receives the action packet and executes the validated
ability.

The off-hand input requires the ArenaSystem dagger marker in both hands. Vanilla main-hand item and
player-entity use is suppressed while both marked daggers are equipped to prevent a second
interaction. Block-use callbacks are not intercepted.

## Embedded dagger resources

The dagger resources live under `src/main/resources/assets/arenasystem/`; they are part of the mod
JAR and are not a separately selectable resource pack.

## Build

Requires JDK 25 and Gradle 9.8.0.

Run from this directory:

~~~bash
gradle --no-daemon clean build
~~~

Output: `build/libs/ArenaSystem_Client-0.2.9.jar`.

The CI workflow compiles the mod and runs the standalone handshake, class-skill input protocol, and
keybind modifier matcher tests. These tests do not replace a live Paper/Fabric integration test.

## Updates and compatibility

Handshake Protocol v2 and the updater implementation remain unchanged. The updater still reads
the repository's existing manifest and release layout. Automatic replacement is Windows-only;
other operating systems require manual installation of the verified staged JAR.

The updater does not update the Paper server plugin.
