# Patch Notes — Class Passive Framework

## Added

- Added `ClassPassiveManager` as the central registry and lifecycle owner for class passives.
- Registered passive concepts for Knight, Archer, Sniper, Tank, Assassin, Ninja, Boxer, and Demoman.
- Added per-player passive state storage for counters, deadlines, and boolean flags.
- Restricted active passive state to alive Survival players in running TDM matches.
- Added cleanup for death, quit, class changes, match stop/reset, and plugin shutdown.
- Added `PASSIVE-SYSTEM.md` describing the registry and state API.

## Important

This is the infrastructure stage only. No passive gameplay effects have been switched on yet,
and no existing class skills, kits, or Ninja snowball behavior were intentionally changed.
