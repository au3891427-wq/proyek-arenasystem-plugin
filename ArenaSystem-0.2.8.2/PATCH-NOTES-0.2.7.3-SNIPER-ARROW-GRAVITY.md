# ArenaSystem 0.2.7.3 — Sniper Arrow Gravity

- Sniper-fired arrows now use `setGravity(false)` so their path does not drop due to Minecraft projectile gravity.
- The change is scoped to the Sniper `EntityShootBowEvent` handler. Archer arrows, Demoman projectiles, and other projectile classes retain their existing gravity behavior.
- Existing Sniper velocity values remain unchanged: 6.2 normally and 6.8 during Focus Mode.

## Validation
- Confirmed the Sniper handler sets arrow gravity to false.
- Confirmed the separate Demoman projectile gravity setting remains true.
- Full Maven/Paper build not run in this environment; the project targets Java 25 and Paper API 26.3+.
