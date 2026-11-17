# ArenaSystem 0.2.7.5 — Compilation Fix

- Fixed `missing return statement` in `preparationSpawnLocation()`.
- Removed the stale `if (singlePlayer)` guard so the team-based switch returns a `Location` for every execution path.
- RED, BLUE, GREEN, and YELLOW continue to use their own preparation stations.
- Updated Maven artifact version, `plugin.yml`, and build helper JAR names to `0.2.7.5`.
- Preserves the 0.2.7.4 Random Stuff right-click handling and team-station spawn changes.
