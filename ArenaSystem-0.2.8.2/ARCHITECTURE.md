# Architecture Overview

## Component Relationships
```mermaid
flowchart TD
    Plugin[ArenaSystemPlugin] --> ArenaManager[ArenaManager]
    Plugin --> MatchManager[MatchManager]
    Plugin --> ClassManager[ClassManager]
    MatchManager --> TeamManager[TeamManager]
    ClassManager --> AbilityManager[ClassAbilityManager]
    ClassManager --> PassiveManager[ClassPassiveManager]
    Plugin --> TerrainScanner[TerrainScanner]
    Plugin --> TerrainNormalizer[TerrainNormalizer]
```

## Data Ownership & Flow
- **Arena Data:** Managed by `ArenaGenerator` and `TerrainNormalizer`, backed by snapshots (`SnapshotStore`).
- **Match State:** Managed by `MatchState` and `MatchManager`, orchestrating team spawning and HUD rendering.
- **Player State:** Captured in `PlayerSnapshot`, tracked via `PlayerArenaListener`.
