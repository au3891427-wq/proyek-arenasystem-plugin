# Upgrade ArenaSystem 0.2.3.8 → 0.2.3.9

1. Stop the server and back up the ArenaSystem plugin folder, worlds, and arena snapshots.
2. Replace the old JAR with `ArenaSystem-0.2.3.9.jar`.
3. Start the server. The plugin migrates `matches.allow-solo-test` to `matches.allow-single-player` and removes the legacy key from the saved configuration.
4. One-player matches remain enabled by default. The gameplay uses the regular match HUD and no extra solo-mode label or announcement.
5. A match with one participant finishes without falsely declaring a winning or losing team. Winner/loser titles are reserved for matches with opposing teams.
6. No arena snapshots or team-count settings are reset by this wording/configuration correction.
