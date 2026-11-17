# Temporary State Registry

| State Owner | Created At | Updated At | Cleanup At |
| :--- | :--- | :--- | :--- |
| **Projectiles / Bombs** | Ability Trigger / Item Use | Tick Scheduler | Death / Match End / Plugin Disable |
| **Death Camera** (follow session + stored offset, expiry timer) | Player Respawn after enemy kill | Tick Observer | Expiry / New Death / Quit / Match End / Plugin Disable |
| **Deferred Snapshot** (player dead at match end) | Match End (`restorePlayerSnapshots`) | - | Player Respawn / Join / Plugin Disable (online players) |
| **Attribute Modifiers** | Class Selection / Buff | Item Use | Class Change / Death / Quit |
| **Temporary Entities** | Random Stuff Spawn | Tick Scheduler | Arena Reset / Plugin Disable |
