# Validation Report — ArenaSystem 0.2.8.6

## Implemented
- Paper update checker is notification-only. The previous `PluginUpdateService` downloader/stager has been removed.
- Manifest is checked over HTTPS from `raw.githubusercontent.com`; release URL is validated against the expected GitHub release/tag.
- Newer versions are announced to console and admins with `arenasystem.admin` or OP. Admins joining later also receive the notice.
- Paper artifact name is `arenaSystem_Bukkit-<version>.jar`.
- Assassin off-hand input uses only `EquipmentSlot.HAND`; duplicate off-hand packets are ignored.
- Fixed rolling debounce: rejected packets no longer refresh the last accepted swing timestamp. The swing interval is 600 ms.
- Off-hand animation is sent one tick after input; miss effects do not emit sweep particles/sweep sound. Hit sound and hit particles run only when damage actually changes health or absorption.
- Client patch renames the Fabric JAR to `arenaSystem_client-<version>.jar` and updates the client updater/helper for versioned filenames.

## Checks performed
- [x] `pom.xml` parsed as XML.
- [x] `plugin.yml` and `config.yml` parsed as YAML.
- [x] `PluginUpdateNotifier.java` compiled with Java 21 against minimal Bukkit API stubs.
- [x] Manifest string-field/schema parsing smoke test passed.
- [x] Four-part version comparison smoke test passed (`0.2.8.6` > `0.2.8.5`).
- [x] Updated Windows helper compiled with Java 21.
- [x] Helper smoke test passed for versioned install, backup retention, legacy in-place compatibility, and checksum rejection.
- [x] ZIP integrity checks passed for generated source bundles.

## Not run / limitations
- Full Maven build against Paper 26.3 / JDK 25 was not run: this environment has Java 21, but Maven and Gradle are not installed.
- Full Fabric Gradle build and GitHub Actions were not run.
- Live Minecraft/Paper testing was not run.
- Delaying `swingOffHand()` by one tick reduces same-tick main/off-hand animation collisions, but cannot guarantee two independent simultaneous arm animations. That stronger behavior requires a client-side renderer/mixin; the current client mod source has no such mixin.
- First migration from the old fixed client JAR filename requires manual replacement once; see `client-mod-patch/README-APPLY.md`.

## Deployment recommendation
Build with JDK 25 and Maven/Gradle, run the existing GitHub Actions, then test on a backup server and client before production. Do not keep both old and new ArenaSystem plugin JARs in `plugins`, or both old and new client JARs in `mods`.
