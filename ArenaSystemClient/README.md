# ArenaSystem Client (Fabric)

Client companion for ArenaSystem Paper, targeting Minecraft 26.3, Fabric Loader 0.20.1,
Fabric API 0.166.2+26.3, and Java 25.

## Identity

- Mod ID: `arstm_client0qb201`
- Legacy alias: `arenasystem_assassin_dagger`
- Version: **0.2.0**
- Artifact: `ArenaSystem_Client-0.2.0.jar`

Install only one ArenaSystem client JAR in the mods directory. Do not keep old and new JARs
side by side.

## Features

- Paper companion handshake on `arenasystem:mod_handshake`, preserving Handshake Protocol v2.
- Startup update check with HTTPS GitHub release downloads and SHA-256 verification.
- Four remappable gameplay key mappings registered under the ArenaSystem Client category.
- Client-side checks for Paper PDC markers before sending class-skill or dagger interactions.
- Embedded `arenasystem:assassin_dagger` item definition, model, and animated 32x128 texture.
- A small local slash effect when a valid dagger input is attempted.

The Paper plugin remains authoritative for class selection, skill availability, target validation,
cooldowns, damage, and successful-hit feedback. The client slash particle is cosmetic feedback for
an input attempt; it is not proof that the server accepted a hit.

## Default controls

Defaults are starting bindings only. Players can remap them in **Options → Controls → Key Binds**.

| Default key | Action |
|---|---|
| G | Primary class skill (Swap Hands) |
| H | Secondary class skill (requires Sneak or Sprint) |
| J | Use a held class skill item |
| K | Assassin Dagger offhand strike |

Primary and secondary both send the vanilla Swap Hands action expected by Paper. The primary
binding is suppressed while Sneak/Sprint is held so it cannot be misread by the server as a
secondary-skill request. The secondary binding sends the same action only while a modifier is
held.

The class-skill-item binding only activates when the main-hand item carries the Paper marker
`arenasystem:class_skill`. The dagger binding requires a Golden Sword carrying
`arenasystem:assassin_blade` in both hands. It uses the main-hand right-click interaction because
ArenaSystem Paper 0.2.8.9 listens for that interaction and performs the offhand swing itself.

## Embedded dagger resources

The dagger resources live under `src/main/resources/assets/arenasystem/`; they are part of the mod
JAR and are not a separately selectable resource pack. The texture animation contains four 32x32
frames stacked vertically. To regenerate it from the original 32x32 resource-pack texture:

```bash
python tools/generate_dagger_animation.py path/to/assassin_dagger.png src/main/resources/assets/arenasystem/textures/item/assassin_dagger.png
```

Pillow is needed only to regenerate the PNG and is not a Gradle or runtime dependency.

## Build

Requires JDK 25 and Gradle 9.8.0.

Run from this directory:

```bash
gradle --no-daemon clean build
```

Output: `build/libs/ArenaSystem_Client-0.2.0.jar`.

The CI workflow also compiles and runs standalone binary protocol tests, including fixed
HMAC-SHA256 test vectors. Those tests do not replace an in-game Paper/Fabric integration test.

## Updates and compatibility

The Handshake Protocol v2 implementation and updater implementation remain unchanged. The updater
still reads the repository's existing manifest and release layout. Automatic replacement is
Windows-only; other operating systems require manual installation of the verified staged JAR.

The updater does not update the Paper server plugin.
