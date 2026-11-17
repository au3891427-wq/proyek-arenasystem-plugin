# Validation Report — ArenaSystem 0.2.7.9

## Source checks
- Version metadata updated consistently in `pom.xml`, `plugin.yml`, Maven final artifact name, and build scripts.
- Dynamite Burst still emits at most three projectiles, one every four ticks, with the existing 1.35-second fuse and 10-second default cooldown.
- Dynamite Burst maximum damage is 4.8 per projectile; blast falloff and 3.5-block radius are unchanged.
- Dynamite Burst target knockback is reduced; Blast Mine keeps its previous knockback and 4.2 maximum damage.
- Pocket Bomb is assigned to Demoman slot 1 as a tagged TNT Minecart item; it is handled through the existing class-skill interaction path.
- Pocket Bomb launches one custom projectile (3-block radius, 3.4 maximum damage, 1.35-second fuse) with a separate 2-second configurable cooldown.
- Pocket Bomb uses TNT Minecart for its native cooldown icon, avoiding a shared cooldown overlay with the Blast Mine TNT item.
- Both Demoman projectile types use the existing `demomanDynamite` registry, detonation checks, hit event, fuse task, and cleanup paths.
- Both custom bomb types use entity-only damage and do not invoke vanilla block explosions.

## Static validation performed
- 22/22 source assertions passed for version/config metadata, burst cap and cadence, damage/knockback tuning, primary-weapon routing, cooldown separation, projectile fuse/registry cleanup, and entity-only explosion handling.
- `config.yml` and `plugin.yml` both parsed successfully as YAML.
- A full Java compile and Paper runtime test have not been completed in this environment.
- Verify on a Paper 26.3 test server: main-hand right-click Pocket Bomb, skill 1 burst max projectile count, damage/armor falloff, reduced vertical knockback, mine behavior unchanged, and cleanup on player quit/death/match end/plugin disable.

## Build status
- Required toolchain: JDK 25, Maven, Paper API 26.3 dependency resolution.
- Environment check: OpenJDK `21.0.12.1` is installed; `mvn` is not installed.
- Actual build attempt: `mvn clean package` returned `bash: mvn: command not found`.
- Build status: NOT VERIFIED. No JAR has been produced or claimed. Do not install this source as a production JAR until `mvn clean package` succeeds with JDK 25 and runtime tests pass.
