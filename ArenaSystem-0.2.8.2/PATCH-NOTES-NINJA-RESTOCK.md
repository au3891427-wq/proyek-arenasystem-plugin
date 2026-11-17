# Patch Notes — Ninja Snowball Restoration

Baseline: `ArenaSystem-0.2.3.7-5-respawn-deathcam-fix(1)`

## Restored
- Ninja starts with 7 snowballs instead of 16.
- Ninja snowballs apply a controlled horizontal/upward knockback to opposing players in the same active TDM match.
- Restock adds one snowball every 4.5 seconds until the total reaches 7.

## Timer behavior
- While the player has 7 or more snowballs, the next-restock deadline is continuously reset.
- After the count drops below 7, a fresh 4.5-second wait is required.
- If the inventory has no room and no existing snowball stack to extend, the due restock retries until a slot becomes available.
- The count includes the offhand slot to prevent bypassing the seven-item cap by moving snowballs there.
- The timer is cleared on death, disconnect, match end, or leaving Ninja status.

The repeating check runs every 5 ticks, so the 4.5-second interval is checked with up to approximately 0.25 seconds of scheduling granularity.
