# Patch Notes — ArenaSystem 0.2.8.0

## DEATH CAMERA — FREE-LOOK FOLLOW
- Replaced the locked `setSpectatorTarget(killer)` view with a manually positioned spectator camera that begins 3.25 blocks behind the killer at approximately head height.
- Camera position follows the killer's current location and facing direction; camera yaw/pitch remain independent so the spectator can look around without changing the follow position.
- WASD/flight translation is blocked while death camera is active; only camera rotation is accepted from player input.
- The player's existing match scoreboard/HUD is explicitly retained during the death-camera phase.
- Follow task and expiry state are cleaned when the spectator disconnects, the match is reset/stopped, or the plugin disables. If the killer dies or leaves, follow updates stop but the camera remains position-locked until the normal death-camera timer ends.
- Existing death-camera duration, respawn spawn, score handling, and post-camera loadout restoration remain unchanged.

## RANDOM STUFF — SUPPORT + OFFENSE
- Expanded reward pool from six support-only items to nine varied materials with custom names/descriptions.
- Support rewards retained: Field Ration (Regeneration), Honey Guard (Absorption), Fleet Arrow (Speed), Aegis Shield (Resistance), and Power Signal (Strength).
- Added Frost Orb (Snowball): 3.0 damage on direct enemy hit and Slowness for 2 seconds.
- Added Ember Shot (Fire Charge): custom Small Fireball, 4.75 maximum impact-area damage within 1.65 blocks. Incendiary behavior and vanilla explosion yield are disabled.
- Added Rift Bomb (Ender Pearl): custom thrown orb, 3.5 impact-area damage within 2.25 blocks and light knockback.
- Added Duelist Blade (Iron Sword): 4.5 damage against the first valid enemy player within 4 blocks, with block collision respected so the hit cannot pass through cover.
- Offensive effects can only damage opposing players enrolled in the same running arena match. Friendly fire and cross-arena damage are excluded.
- Custom projectiles are tagged, their vanilla projectile damage is cancelled, and their lifetime is capped at 4 seconds. Existing main-hand right-click activation and vanilla-item-action suppression are retained.

## COMPATIBILITY / BUILD
- Project/plugin version bumped to 0.2.8.0. Java target remains 25, Paper API target remains 26.3, and Maven compiler plugin remains 3.13.0.
- No match HUD layout or arena generation logic was intentionally changed.
- Full Maven compilation and Paper runtime testing must be completed with JDK 25 before production use.
