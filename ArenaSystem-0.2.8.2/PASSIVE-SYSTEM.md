# Class Passive System

The central `ClassPassiveManager` owns per-player passive state for active TDM participants. It handles timers, counters, projectile markers, damage modifiers, and cleanup after death, disconnect, class changes, match stop, and plugin shutdown.

## Implemented passives

| Class | Passive | Gameplay behavior |
|---|---|---|
| Knight | Guard | Reduces damage from opposing TDM players by 6%. |
| Archer | Steady Aim | After remaining in the same position for 1 second, bow-fired arrows released while stationary gain +10% damage against an opposing player. Moving resets the aim timer. |
| Sniper | Precision | Arrows that hit an opposing player at least 16 blocks from the launch position deal +10% damage. |
| Tank | Bulwark | Reduces incoming opposing-player damage by 5%. Every 3.5 seconds, there is a 15% chance to absorb 50% of the remaining damage. |
| Assassin | Executioner | Direct melee attacks deal +12% damage when the opposing player is at or below 35% of maximum health. |
| Ninja | Shadow Veil | Sneak without attacking or taking a hit for 5.6 seconds to gain Speed II and full vanish for 5.3 seconds. The first successful hit on an opposing player gains +20% damage. |
| Boxer | Rhythm | Every 3 successful unarmed melee hits on opposing players reduces the active `boxer_rapid_jab` cooldown by up to 4.5 seconds. No hits are banked while Skill 1 is ready; cooldown never becomes negative. |
| Demoman | Demolition Expert | Reduces the horizontal recoil of the Demoman's own Dynamite blast by 30% and vertical recoil by 20%. The custom Dynamite blast calls `markDemomanOwnExplosion(player)` before applying the owner's recoil. Ordinary explosions are not modified. |

## Shared lifecycle rules

- Passive state exists only for alive, online players in Survival mode during a running TDM match.
- Preparation lobbies, Relic Rush, spectators, dead players, and offline players do not hold active passive state.
- A class or arena change replaces the player's old passive state.
- Death, disconnect, match stop/reset, and plugin shutdown clear transient passive data.
- Projectile modifiers are tied to the projectile owner, arena, and a 30-second expiry to avoid stale bonuses.

## Shadow Veil and Smoke Bomb

Both use the same `ClassAbilityListener.startVanishTicks(...)` implementation. This means Shadow Veil applies potion invisibility and hides the complete player entity from every other online viewer through `hidePlayer(plugin, player)`, including armor and held items. The Ninja is excluded from the viewer-hiding loop, so the player's own view behaves like Smoke Bomb. The hide state is refreshed for viewers who join or begin tracking during the vanish.

## Boxer Rhythm details

- Only direct, unarmed `ENTITY_ATTACK` hits against an opposing TDM player count.
- Custom Rapid Jab/Barrage ability hits do not count as basic attacks.
- Skill 1 is `boxer_rapid_jab` (Rapid Jab), the new three-punch combo.
- The passive counts only while Skill 1 has an active cooldown; hits are not banked while ready.
- At 3 landed basic hits, 4.5 seconds (or the entire remaining cooldown if less) is removed.
- The native Slime Ball item cooldown display is updated to match the authoritative cooldown timestamp.

## Boxer active skills

- Rapid Jab: up to three quick forward punches, 1.15 damage each, 16-second cooldown.
- Barrage: up to six quick punches against the initially targeted opponent, 0.90 damage each, 28-second cooldown. The sequence stops if the target leaves close range or line of sight.
- Scripted punches intentionally reset vanilla hit-immunity ticks between low-damage hits so the combo can register as multiple hits.

## Balance values

The numeric values in this file are initial balancing values. They can be adjusted after live tests without changing the passive lifecycle framework.
