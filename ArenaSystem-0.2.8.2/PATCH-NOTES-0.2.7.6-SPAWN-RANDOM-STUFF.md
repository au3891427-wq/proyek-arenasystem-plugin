# ArenaSystem 0.2.7.6 — Preparation Spawn & Random Stuff Input

- Replaced `preparationSpawnLocation()` switch expression with explicit return branches to make every return path unambiguous for the compiler.
- Moved each preparation spawn slightly inward on its own colored team pad, away from the marker block.
- Added a one-tick team-station reassertion after the lobby teleport; the selected team remains the source of truth even in solo mode.
- Random Stuff is detected in the hand that generated the interaction, including off-hand input. Every interaction with a tagged item is cancelled; only main-hand right-click activates its custom effect.
- Suppressed vanilla block damage, block breaking, entity interaction, melee damage, item consumption, and projectile launches while the relevant tagged Random Stuff item is used.
- Kept Random Stuff's drop/expiry behavior and custom reward effects.
- Updated Maven/plugin/build metadata to version 0.2.7.6.

## Build verification

- ZIP archive integrity checked after packaging.
- Full Maven/Paper compilation was not run in this environment; compile with JDK 25 and the Paper 26.3 API used by the server.
