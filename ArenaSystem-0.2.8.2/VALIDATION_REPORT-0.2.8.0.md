# Validation Report — ArenaSystem 0.2.8.0

## Source audit and implementation checks
- Baseline inspected: `ArenaSystem-0.2.7.9-source.zip`; no older source archive was used as a replacement.
- Death-camera entry still requires the valid opposing killer in the same active match.
- Camera starts 3.25 blocks behind the killer at approximately head height. Its location follows the killer's position and facing direction every tick; camera yaw/pitch are retained independently.
- `PlayerMoveEvent` locks spectator X/Y/Z while allowing yaw/pitch input. The task marks its own teleports so the movement guard does not block follow updates.
- The match HUD scoreboard is not cleared or rebuilt during death-camera activation; the current HUD scoreboard is explicitly retained.
- Camera state/tasks are cleaned on viewer quit, match reset/stop, and plugin disable. If the killer dies or disconnects first, camera position remains locked until the existing death-camera expiry.
- Random Stuff pool now has 9 distinct item materials: 5 support and 4 offensive.
- Frost Orb: 3.0 direct damage and 2 seconds of Slowness. Ember Shot: 4.75 impact-area damage within 1.65 blocks. Rift Bomb: 3.5 impact-area damage within 2.25 blocks and light knockback. Duelist Blade: 4.5 hit-scan damage at up to 4 blocks; block collision is respected so cover blocks the hit.
- Offensive damage requires both players to be online, alive, in the same running arena, registered in the match, and on opposing teams.
- Tagged custom projectiles have vanilla projectile damage cancelled. SmallFireball incendiary behavior and vanilla explosion yield are disabled; custom damage is handled by the plugin.
- Existing Random Stuff token ownership/expiry, main-hand right-click activation, item drop cleanup, and vanilla action suppression remain in place.

## Static validation performed
- **32/32 source assertions passed**: version consistency, camera tracking/rotation lock/HUD retention/cleanup, nine-item pool composition, support/offense reward mapping, projectile tagging, vanilla-damage suppression, team validation, and right-click-only activation.
- Java parser check: **21 Java files parsed, 0 syntax parse errors** using the locally available JDK 21 compiler parser. This is a syntax check only, not a Java 25 compilation.
- `pom.xml` parsed successfully.
- `config.yml` and `plugin.yml` both parsed successfully as YAML (2/2).
- Paper 26.3 Javadocs were checked for the relevant spectator, combined block/entity `World#rayTrace(...)`, `SmallFireball`/`Explosive`, and projectile API contracts. The Duelist Blade ray trace now respects block collision.
- ZIP integrity is checked separately after packaging.

## Runtime checklist (Paper 26.3)
1. Kill an arena player with an opposing player. Confirm the dead player enters spectator view behind the killer.
2. Move and rotate the killer. Confirm the camera position tracks behind the killer while spectator mouse yaw/pitch remain independent.
3. Press WASD, jump, and sneak while spectating. Confirm the camera does not detach; mouse movement still rotates the view.
4. Confirm the match sidebar/team HUD remains visible for the full death-camera interval.
5. Test killer death/disconnect, viewer disconnect, match stop/reset, and plugin disable. Confirm no repeating camera task remains and normal snapshot restoration still works.
6. Test all nine Random Stuff rewards. Confirm support effects work; offensive items damage only registered opponents in the same arena and cannot damage teammates or players outside the match.
7. Confirm Fireball does not ignite blocks or damage terrain; confirm all custom projectiles disappear after hit or their four-second lifetime.
8. Confirm Random Stuff still activates only from main-hand right-click and normal vanilla item actions remain suppressed.

## Build status
- Required target: JDK 25 + Maven + Paper API 26.3; `maven-compiler-plugin` 3.13.0.
- Environment check: OpenJDK `21.0.12.1` is installed; JDK 25 is not available and `mvn` is not installed.
- Actual build attempt from the project directory: `mvn clean package` returned exit code `127` with `bash: mvn: command not found`.
- Build status: **NOT VERIFIED**. No JAR was produced or claimed. Run `mvn clean package` with JDK 25, then complete the runtime checklist on a disposable Paper 26.3 server before deploying to the live server.
