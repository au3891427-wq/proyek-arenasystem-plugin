# Patch Notes — ArenaSystem 0.2.7.9

## DEMOMAN — DYNAMITE BURST
- Reduced target knockback for Dynamite Burst: horizontal impulse now scales from 0.192 to 0.340 and vertical impulse from 0.0585 to 0.120, based on blast falloff. This is an impulse added to existing velocity, not an absolute velocity cap.
- Increased maximum per-projectile damage from 3.6 to 4.8 before armor and other event-based mitigation (approximately 33.3%). Damage falloff and the 3.5-block radius are unchanged.
- Blast Mine retains its previous damage and knockback values.

## DEMOMAN — PRIMARY WEAPON
- Replaced the Stone Sword in slot 1 with **Pocket Bomb**, represented by a TNT Minecart item.
- Right-click throws one custom TNT-looking projectile. It detonates on projectile impact or after its 1.35-second fuse, with a 3-block radius and 3.4 maximum damage before mitigation.
- Pocket Bomb has an independent 2-second configurable cooldown (`class-abilities.demoman_primary_bomb.cooldown-seconds`). Its native item cooldown uses TNT Minecart, separate from the Blast Mine TNT item.
- Pocket Bomb is routed through the plugin's entity-only explosion system. It does not place a minecart/TNT block, create a vanilla explosion, or damage terrain.
- The thrown projectile uses the same owner/match/class validation and Demoman projectile cleanup as Dynamite Burst.

## VERSION / CONFIG
- Bumped project/plugin version to 0.2.7.9.
- Added the default `demoman_primary_bomb` cooldown setting (2 seconds).
- Kept Skill 1's three sequential projectiles, 0.2-second spacing, 1.35-second fuse, existing cooldown, and cleanup lifecycle.

## BUILD STATUS
- Source targets Java 25, Paper API 26.3, and `maven-compiler-plugin` 3.13.0.
- A successful Maven build is not claimed until `mvn clean package` is actually run using the required toolchain.
