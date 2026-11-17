# Source Code Map

| Feature / Module | Main Class | Associated Classes / Listeners | Entry Point / Trigger |
| :--- | :--- | :--- | :--- |
| **Arena Generation** | `ArenaGenerator` | `TerrainNormalizer`, `TerrainDetector` | Command / Match Start |
| **Class Abilities** | `ClassAbilityListener` | `ClassAbilityManager`, `DemomanAbilities` | Right-Click / Item Interaction |
| **Random Stuff** | `RandomStuffManager` | `RelicListener`, `PlayerArenaListener` | Chest Loot / Spawning |
| **Death Camera** | `DeathCameraManager` | `PlayerArenaListener` | Player Death Event |
| **Terrain Scanner** | `TerrainScanner` | `TerrainProfile`, `TerrainDetector` | Arena Setup / Validation |
