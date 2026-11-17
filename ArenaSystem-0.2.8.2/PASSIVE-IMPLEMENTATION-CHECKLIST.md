# Passive Implementation Test Checklist

## Solo checks

1. **Ninja — Shadow Veil:** choose Ninja, hold Sneak for 5.6 seconds without swinging or taking a hit. Confirm Speed II, full vanish, and expiry after 5.3 seconds. Repeat to confirm a fresh 5.6-second quiet window is required.
2. **Archer — Steady Aim:** choose Archer, stand still for at least 1 second, shoot an arrow at a mob or wall for visual feedback. Confirm `STEADY AIM` action bar appears. Move, wait less than 1 second, and shoot again; no aim message should appear.
3. **Sniper — Precision:** shoot an arrow from a known position. Long-range damage bonus requires a second player at least 16 blocks from the launch point, so this is not fully verifiable solo.
4. **Tank — Bulwark:** damage reduction and absorption require an opposing player; inspect action bar `BULWARK` when the random absorption proc occurs.
5. **Knight — Guard:** damage reduction requires an opposing player and can be verified by comparing incoming damage against a Knight and a non-Knight class under equal conditions.
6. **Assassin — Executioner:** requires an opposing player at or below 35% maximum health; check the `EXECUTIONER` action bar after a direct melee hit.
7. **Boxer — Rhythm:** choose Boxer, use Skill 1 once so it enters cooldown, then land three unarmed melee hits on an opposing player. Confirm the `RHYTHM` action bar and the Skill 1 cooldown reduction. Hits that miss, hit teammates, use a weapon, or occur while Skill 1 is ready must not count.
8. **Demoman — Demolition Expert:** not testable until Demoman is selectable and its own explosive abilities call the passive's recoil marker hook.

## Two-player verification

- During Shadow Veil, a second player must be unable to see the Ninja's model, armor, or held items. The Ninja should continue seeing their own first-person view as with Smoke Bomb.
- Confirm Shadow Veil's first successful hit gets +20% damage and ends vanish immediately.
- Confirm Archer and Sniper projectile bonuses only affect their owner's arrows and only against opponents in the same TDM match.
- Confirm Knight and Tank defenses apply only to damage from an opposing TDM player, not teammates or players outside the match.
- Confirm Assassin's Executioner does not trigger above the 35% threshold.
- Confirm Boxer only counts landed, unarmed enemy hits and never reduces Skill 1 below zero cooldown.
