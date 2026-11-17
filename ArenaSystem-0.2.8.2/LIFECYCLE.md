# Match & Player Lifecycle

1. **Player Join:** Player snapshot saved (`PlayerSnapshot.java`), teleported to lobby.
2. **Preparation & Class Selection:** GUI opened (`ClassMenuListener.java`), team assigned.
3. **Match Start:** Arena generated/normalized, players spawned facing target orientation.
4. **Combat:** Abilities and passives active; protection listeners prevent griefing (`BlockProtectionListener.java`).
5. **Player Death & Respawn:** Death camera activated, inventory restored via snapshot upon respawn.
6. **Match End & Arena Reset:** Scores tallied, terrain restored to snapshot state, players returned.
7. **Plugin Shutdown:** All active tasks cancelled, temporary entities cleared.
