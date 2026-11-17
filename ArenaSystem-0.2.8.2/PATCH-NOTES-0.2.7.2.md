# ArenaSystem 0.2.7.2

## Random Stuff ground cleanup
- Random Stuff can be intentionally dropped; it remains collectible for up to 5 seconds.
- Items ejected because the player's inventory is full receive the same 5-second cleanup.
- The expiry deadline is stored on the dropped entity, so an unloaded chunk is handled when it loads again.
- Replacing/expiring a player's Random Stuff removes matching copies from their inventory and loaded ground entities.
- Sniper aim-lock's Q/drop handler ignores Random Stuff, so dropping the support item does not toggle aim-lock.

## Validation
- ZIP integrity and source-level assertions checked.
- The existing Sniper projectile trajectory was left unchanged. Full Maven/Paper build not run in this environment; the project targets Java 25 and Paper API 26.3+.
