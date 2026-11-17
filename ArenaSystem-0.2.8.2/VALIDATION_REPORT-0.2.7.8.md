# Validation Report — ArenaSystem 0.2.7.8

## Baseline
- Source baseline: `ArenaSystem-0.2.7.7-team-spawn-facing.zip`.
- Version incremented to `0.2.7.8`.
- Build contract retained: Paper API `26.3.build` range, Java release 25, Maven Compiler Plugin 3.13.0.

## Static source assertions
The following 20 source/configuration assertions passed:
- Maven/plugin version, Java 25 release, Paper 26.3 API range, and compiler plugin version are synchronized.
- Boxer Heavy Jab handles entity interaction, uses a 2.1-second cooldown, and includes a short duplicate-input guard.
- Boxer attack speed uses the `ATTACK_SPEED` attribute with base value 6.0.
- Demoman Dynamite is capped at three launches and schedules one launch every four ticks.
- Blast Mine markers are hidden from existing non-owner viewers and joining non-owners.
- Ninja follow-up is configured for five landed hits at attack speed 20.0, uses final-damage accounting, and restores the original base value.
- Sniper AIM LOCK follows the stored owner-to-target UUID mapping and validates that both players remain in the same active match.
- All three repeating `ClassAbilityListener` tasks have explicit cancellation in `cleanupAll()`.
- Plugin descriptor reports version `0.2.7.8`.

Result: **20/20 assertions passed**.

## Java parser attempt
- `javac -proc:none --release 21` was run over all Java source files as a parser-level check.
- It could not type-check the project because no Bukkit/Paper API dependency is available on the classpath (`org.bukkit` packages were not found).
- No syntax-shaped diagnostics (`expected`, `illegal start`, `reached end of file`, etc.) appeared in that attempt.
- This is not a successful compile and does not replace the requested Java 25/Paper API build.

## Maven build status
- `mvn clean package` was attempted.
- Result: `bash: mvn: command not found` (exit code 127).
- Installed Java runtime/compiler: OpenJDK 21.0.12. The project requires Java 25.
- No JAR is produced or included because Maven build could not be executed.

## Runtime validation still required
Before production deployment, test these flows on a Paper 26.3 server with Java 25:
1. Boxer: right-click an opponent entity with an empty main hand; verify one Heavy Jab per input, 2.1-second cooldown, and faster attack cooldown; verify the base attribute restores after match.
2. Demoman: activate Dynamite; verify exactly three projectiles at 4-tick spacing, then test death/quit/match stop mid-burst. Place Blast Mine and verify only owner sees its marker while opponents can still trigger it.
3. Ninja: trigger a confirmed Shadow Veil ambush, then land five subsequent hits; verify the trigger hit is excluded, misses do not consume a hit, re-trigger does not stack, and all exit/death/class/match cleanup restores the original attack-speed base.
4. Sniper: mark an opponent, activate Q/AIM LOCK, then test target movement, death, disconnect, arena exit, mark expiry, and another Sniper replacing the mark.
5. Regression: test team counts, lobby/spawns, HUD, loadout restoration, Random Stuff, arena protections, terrain normalization/detection, Relic Rush, and TDM.
