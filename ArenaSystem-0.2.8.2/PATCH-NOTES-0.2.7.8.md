# Patch Notes — ArenaSystem 0.2.7.8

## BOXER
- Heavy Jab now accepts main-hand right-clicks on entities through `PlayerInteractEntityEvent`, in addition to air/block interactions.
- Added a 100 ms per-player interaction guard so overlapping interaction packets cannot trigger two Heavy Jabs from one input.
- Changed Heavy Jab cooldown from 3.0 seconds to exactly 2.1 seconds.
- Increased the Boxer `Attribute.ATTACK_SPEED` base value to 6.0. Other classes return to their own class value (4.0 normally, 5.5 for Assassin); player snapshots still restore pre-match values.

## DEMOMAN
- Replaced simultaneous Dynamite Volley creation with a three-projectile burst: one projectile every 4 ticks (0.2 seconds), each with a 1.35-second fuse, with a hard maximum of three per activation.
- Each projectile has its own fuse and remains covered by the existing entity-only blast implementation.
- Burst tasks are tracked and cancelled, and already-launched projectiles are removed when the Demoman is cleaned up.
- Blast Mine's Creeper marker is now hidden from every online viewer except its owner. Players joining later also hide existing markers; the mine's server-side trigger, expiry, and entity cleanup remain unchanged.

## NINJA
- A finalized Shadow Veil ambush hit activates a temporary attack-speed value of 20.0 for the next five landed damage hits.
- The ambush hit itself is excluded. Cancelled/zero-damage events and missed swings do not consume a hit.
- Re-triggering refreshes the five-hit counter without stacking modifiers; the original class attack-speed base is restored after hit five.
- Death, quit, class-state replacement, match cleanup, and plugin shutdown restore the saved attack-speed base.
- Baseline Ninja weapon damage was not changed: the kit still uses a Stone Sword and has mobility/stealth benefits. Retain current damage pending controlled PvP playtesting rather than buffing without evidence.

## SNIPER
- Hardened Mark validation against dead/offline/invalid players, class changes, team changes, match changes, world changes, and either player leaving the marked arena footprint.
- AIM LOCK now validates the owner-to-target mapping before every tracking update and stops/cleans the mark as soon as it becomes invalid.
- Target death/quit, owner death/quit, mark expiry, replacement by another Sniper, and three confirmed Sniper-arrow hits now clear both directions of the mapping and remove the Glowing effect.
- Q/AIM LOCK checks that the marked target is still valid before toggling.

## LIFECYCLE / MAINTENANCE
- Stored the three repeating `ClassAbilityListener` task handles and cancel them during plugin cleanup.
- Added cleanup for Heavy Jab interaction guard state, Demoman burst tasks, and Sniper mark effects.
- Preserved the existing arena, team, lobby, loadout, HUD, respawn, terrain, Relic Rush, TDM, and Random Stuff systems.

## Build status
- Source target remains Java 25 with Paper API 26.3 and `maven-compiler-plugin` 3.13.0.
- This patch package has not been built in this environment: only JDK 21 is installed, Maven is not installed, and external package downloads are unavailable. No JAR is claimed as a successful build.
