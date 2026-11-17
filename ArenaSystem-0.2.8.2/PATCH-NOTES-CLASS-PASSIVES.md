# Class Passive Gameplay Implementation

Implemented gameplay effects for all eight selectable TDM classes. The subsequent Boxer & Demoman update connects Demolition Expert to Demoman's custom Dynamite recoil.

- Knight Guard: 6% reduction to PvP damage from opponents.
- Archer Steady Aim: after 1 second stationary, arrows receive +10% damage on an opponent.
- Sniper Precision: arrows deal +10% damage at 16+ blocks from launch position.
- Tank Bulwark: 5% general PvP damage reduction plus a 15% chance to absorb half of the remaining damage, with 3.5-second internal cooldown.
- Assassin Executioner: +12% direct melee damage against opponents at or below 35% maximum health.
- Ninja Shadow Veil: existing 5.6-second Sneak quiet-window, Speed II, and 5.3-second full vanish remains intact.
- Boxer Rhythm: every 3 successful unarmed basic attacks against opponents removes up to 4.5 seconds from Skill 1 (`boxer_rapid_jab`) cooldown. Custom skill hits do not count.
- Demolition Expert: reduces the owner's custom Dynamite recoil by 30% horizontally and 20% vertically.

Shadow Veil and Smoke Bomb share one vanish implementation, so armor and held items are hidden from other viewers in exactly the same way.


## Boxer and Demoman gameplay update

- Reworked Boxer from Uppercut/Second Wind into Rapid Jab (3-hit forward combo) and Barrage (up to 6 quick hits on one target).
- Boxer Rhythm now reduces Rapid Jab cooldown by 4.5 seconds for every 3 successful unarmed basic hits against opponents. Skill hits are excluded; missed attacks and teammate hits do not count; cooldown cannot become negative.
- Added Demoman to the TDM class menu and `/arena class` selection.
- Dynamite is a short-fuse custom projectile with an entity-only area blast.
- Blast Mine can be placed on a valid arena surface, triggers when an enemy approaches, and expires after 20 seconds. One active mine per Demoman.
- Both explosive abilities are custom effects: they do not create vanilla TNT explosions or damage arena blocks.
- Demolition Expert reduces recoil from the owner's Dynamite blast.
