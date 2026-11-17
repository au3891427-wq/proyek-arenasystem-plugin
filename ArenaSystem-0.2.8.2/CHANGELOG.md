# Changelog

## 0.2.8.2
- Plugin disable: a player who is still dead is respawned synchronously and only then restored; no scheduler is used while the plugin is disabled, so `PlayerSnapshot.restore()` never runs on a dead player.
- Deferred snapshots (players dead at match end) are now also finished in `onDisable()` for online players.
- `onPlayerRespawn` keeps the deferred snapshot in the pending map until the restore actually runs; a guarded `remove` prevents double restoration.

## 0.2.8.1
- Death Camera: camera offset is now computed once when the session starts and reused every tick, so the camera no longer orbits when the killer turns (C1).
- Final-kill lifecycle: snapshot restoration is deferred for players who are still dead when a match ends; a respawn is requested and the snapshot is restored after the player respawns (C3).
- A new death now clears the previous camera session's follow task, expiry timer and stale pending state without touching the new respawn request (C6).
- Death-camera viewers can no longer switch spectator target through click or the spectator menu (C4).
- Movement guard ignores rotation-only move events; no flight-speed change was made (C2 audited, not runtime verified).

## 0.2.8.0
- Replaced the locked spectator entity view after a kill with a third-person follow camera positioned behind the killer. Camera yaw/pitch remain independently controllable while its position tracks the killer; player movement input cannot detach it.
- Kept the player's existing match scoreboard/HUD during the death-camera phase and added follow/expiry cleanup for quit, match reset/end, and plugin disable.
- Expanded Random Stuff from six all-support rewards to nine varied items: five support effects and four offensive tools.
- Added Frost Orb (3.0 direct damage + 2-second Slowness), Ember Shot (4.75 impact-area damage), Rift Bomb (3.5 area damage + light knockback), and Duelist Blade (4.5 close-range hit-scan damage). Duelist Blade ray tracing respects block collision so cover blocks the hit.
- Preserved main-hand right-click activation and cancellation of vanilla item actions. Custom offensive projectile damage is team-validated and does not trigger vanilla fireball block damage.

## 0.2.7.9
- Reduced knockback from Demoman's Dynamite Burst so targets are staggered rather than launched upward; Blast Mine keeps its previous knockback.
- Increased each Dynamite Burst projectile's maximum damage from 3.6 to 4.8 before armor/other mitigation.
- Replaced Demoman's Stone Sword main weapon with Pocket Bomb (TNT Minecart), a right-click custom single-bomb throw with a separate 2-second cooldown, 3-block radius, and 3.4 maximum damage.
- Pocket Bomb and Dynamite Burst use the existing entity-only blast pipeline; neither places vanilla TNT nor damages arena blocks. Their projectiles remain included in Demoman cleanup.

## 0.2.7.8
- Fixed Boxer Heavy Jab activation on right-clicked entities; added same-input de-duplication and set cooldown to 2.1 seconds.
- Set Boxer attack speed to 6.0 through `Attribute.ATTACK_SPEED`, with normal class/snapshot restoration.
- Changed Demoman Dynamite into a three-shot sequential burst with 0.2-second spacing and lifecycle task cleanup.
- Made Blast Mine's marker owner-only while retaining server-side mine detection and cleanup.
- Added Ninja Shadow Veil follow-up: five subsequent landed hits use 20.0 attack speed; the ambush hit is excluded and the base value is restored without modifier stacking.
- Hardened Sniper Mark/AIM LOCK validation and bidirectional cleanup for invalid targets, deaths, exits, replacements, expiry, and completed marks.
- Stored/cancelled repeating class ability tasks during plugin shutdown.

## 0.2.7.6
- Made preparation team spawn explicit and reasserted it one tick after lobby placement.
- Random Stuff activates only from main-hand right-click; vanilla block/entity/item interactions are suppressed while it is held.
- Replaced the preparation spawn switch expression with explicit returns to avoid the reported missing-return compilation path.


## 0.2.7.5
- Fixed a missing-return compilation error in `preparationSpawnLocation()` by making team-station selection unconditional.
- Solo mode still uses the station assigned to the selected team; the solo flag only affects the minimum-player requirement.
- Synchronized Maven, plugin descriptor, and build-script version/output names to 0.2.7.5.


## 0.2.7.3
- Disabled Minecraft gravity for arrows fired by the Sniper class; the arrow keeps its launch direction instead of dropping over distance.
- Archer arrows and non-Sniper projectiles retain their previous gravity settings.

## 0.2.7.2
- Random Stuff dropped on the ground is cleaned up after 5 seconds, including inventory-overflow drops and unloaded chunk reloads.

- Solo match handling stays internal and uses the regular match HUD; no solo-test announcement or label is shown.
- Single-player config is named `matches.allow-single-player`; the previous key migrates automatically.
- A one-player match does not display a misleading winner/loser result.

## 0.2.3.8 — Final audit

- Integrated vegetation cleanup into the snapshot-backed terrain generation plan; legacy cleanup now scans a taller vertical band and re-runs once under v4.
- Simplified TDM start transition and removed the duplicate note-block start sound.
- Corrected GREEN/YELLOW spawn pad height handling.
- Updated Sniper Mark tooltip, clarified 2-4 team command help, and removed a duplicated Focus Mode sound.

## Blast Mine Creeper Marker
- Replaced the previous ArmorStand/TNT marker with a stationary, glowing, non-combat Creeper visual.
- Marker is removed immediately when the mine triggers and on all existing cleanup/expiry paths.
- Mine owner receives a notification only when an opponent actually loses health or absorption to the mine.

# Changelog

## 0.2.8.0
- Replaced the locked spectator entity view after a kill with a third-person follow camera positioned behind the killer. Camera yaw/pitch remain independently controllable while its position tracks the killer; player movement input cannot detach it.
- Kept the player's existing match scoreboard/HUD during the death-camera phase and added follow/expiry cleanup for quit, match reset/end, and plugin disable.
- Expanded Random Stuff from six all-support rewards to nine varied items: five support effects and four offensive tools.
- Added Frost Orb (3.0 direct damage + 2-second Slowness), Ember Shot (4.75 impact-area damage), Rift Bomb (3.5 area damage + light knockback), and Duelist Blade (4.5 close-range hit-scan damage). Duelist Blade ray tracing respects block collision so cover blocks the hit.
- Preserved main-hand right-click activation and cancellation of vanilla item actions. Custom offensive projectile damage is team-validated and does not trigger vanilla fireball block damage.

## 0.2.3.7-5 — Boxer Rework & Playable Demoman

- Reworked Boxer from Uppercut/Second Wind into a close-range, shotgun-inspired unarmed combo class.
- Added **Rapid Jab**: up to three quick forward punches (1.15 damage each, 3.4-block reach, 16-second cooldown).
- Added **Barrage**: up to six quick punches on the initially targeted opponent (0.90 damage each, 3.4-block reach, 28-second cooldown); the sequence stops when target range or line of sight is lost.
- Kept Boxer Rhythm: every 3 successful ordinary unarmed hits against opponents reduces Rapid Jab cooldown by up to 4.5 seconds. Scripted skill hits do not count; no cooldown reduction is banked while Skill 1 is ready.
- Added Demoman to class selection menu and `/arena class demoman`.
- Added **Dynamite**: custom fire-charge-looking projectile that detonates on impact or after a short fuse, dealing distance-scaled damage and knockback to enemy players.
- Added **Blast Mine**: one active mine per Demoman, triggers when an enemy approaches, and expires after 20 seconds.
- Added Demolition Expert integration: reduces the owner's Dynamite recoil by 30% horizontally and 20% vertically.
- Both Demoman explosives use entity-only custom effects: no vanilla explosion event, block damage, terrain edits, or structure destruction. Teammates and players outside the same TDM match are excluded.
- Added configurable cooldown defaults for all four new skill IDs in `config.yml`.
- Added `BOXER-DEMOMAN-TEST.md` with two-player gameplay, arena-safety, and cleanup test cases.

## 0.2.3.7-5 — All Selectable Class Passives

- Implemented Knight Guard: 6% reduction to damage from opposing TDM players.
- Implemented Archer Steady Aim: stationary for 1 second to mark bow-fired arrows for +10% damage.
- Implemented Sniper Precision: +10% arrow damage against opponents at least 16 blocks from the launch position.
- Implemented Tank Bulwark: 5% PvP damage reduction plus a 15% chance to absorb half the remaining damage, with a 3.5-second internal cooldown.
- Implemented Assassin Executioner: +12% direct melee damage against opponents at or below 35% maximum health.
- Retained Ninja Shadow Veil and its shared full-vanish implementation with Smoke Bomb.
- Implemented Boxer Rhythm: every 3 successful unarmed enemy hits removes up to 4.5 seconds from the active Skill 1 cooldown.
- Registered Demolition Expert's own-blast recoil hook; it is activated by Demoman's custom Dynamite blast in the Boxer & Demoman update.
- Kept passives scoped to active TDM opponents and cleared tracked state/projectile markers on lifecycle transitions.
 — ArenaSystem 0.2.3.7-5

## Particle effects untuk skill class
- Knight Guard menampilkan efek blok besi sebagai visual perlindungan.
- Tank Endurance menampilkan aura Totem; Ground Slam mempertahankan pecahan blok dan menambahkan debu gelombang kejut.
- Assassin Burst menampilkan percikan api; Venom Edge memakai partikel Witch saat disiapkan dan mengenai target.
- Ninja Smoke Bomb dan Shadow Step tetap memakai efek Smoke dan Portal.
- Archer Volley menampilkan jejak Critical; Pinning Shot memakai efek salju saat disiapkan/terkena target.
- Sniper Mark menampilkan percikan listrik dan End Rod pada target; Focus Mode menampilkan aura End Rod saat aktif dan kilatan listrik pada setiap panah yang ditembakkan selama mode aktif.
- Boxer Uppercut/Second Wind particle effects belonged to the previous kit and are superseded by Rapid Jab/Barrage in the Boxer & Demoman update.
- Efek hanya dipancarkan saat aktivasi, pelepasan, atau dampak skill yang relevan agar partikel tidak memenuhi arena secara terus-menerus.

## Upgrade skill class
- **Sniper Mark** sekarang memberi Glowing dan meningkatkan damage yang diterima target dari serangan pemain lawan sebesar 20% selama 5 detik.
- Target Mark menerima notifikasi yang menyebutkan nama Sniper yang menandainya.
- Kecepatan panah Sniper dinaikkan menjadi 6.2 blok/tick (normal) dan 6.8 saat Focus Mode, dengan gravitasi alami tetap aktif.
- Mengubah Skill 2 Sniper menjadi Focus Mode berdurasi 8 detik (+15% damage untuk semua panah, cooldown 25 detik).
- Historical skill expansion: Knight — Rally; Tank — Ground Slam; Assassin — Venom Edge; Archer — Pinning Shot; Sniper — Focus Mode; Boxer — Second Wind (later replaced by Rapid Jab/Barrage).
- Ninja tetap menggunakan Shadow Step sebagai Skill 2.
- **F** mengaktifkan Skill 1, **Shift+F** atau **Sprint+F** mengaktifkan Skill 2. Klik kanan item skill tetap didukung.
- Cooldown Skill 2 dapat diubah pada bagian `class-abilities` di `config.yml`.

## Perubahan sebelumnya
- Memperbaiki modifier skill kedua Ninja agar membaca input tombol langsung dari Paper, bukan hanya status sneak.
- **Shift+F** tetap mengaktifkan **Shadow Step** saat Shift di-bind ke Sprint maupun Sneak.
- **F** tanpa modifier tetap mengaktifkan **Smoke Bomb** untuk Ninja; class lain tetap memakai F untuk skill utama.
- Aktivasi tetap memakai `PlayerSwapHandItemsEvent`; pertukaran offhand dibatalkan.
- Seluruh fitur versi 0.2.3.8 dan sebelumnya dipertahankan.

## Catatan input
Paper menyediakan `Player#getCurrentInput()` dengan status input `isSneak()` dan `isSprint()`. Handler menggunakan kedua status tersebut untuk menentukan modifier yang sedang ditahan.

## Class selector pagination

- Split the TDM class selector into two pages with four class cards per page.
- Class cards use slots 10, 12, 14, and 16, leaving one separator slot between cards.
- Added Previous, page indicator, and Next controls in slots 18, 22, and 26.
- Cancelled click and drag interactions that could move selector items.


## ArenaSystem 0.2.5 — Blast Mine Marker Fix
- Memperbaiki konflik antara spawn Creeper penanda Blast Mine dan MobProtectionListener.
- Menandai Creeper dekoratif dengan scoreboard tag internal sebelum entity dimasukkan ke world.
- MobProtectionListener sekarang hanya memberi pengecualian kepada marker tersebut; mob biasa tetap diblokir dan dibersihkan dari arena.
- Penanda juga dikecualikan dari cleanup chunk-load, pembatasan gerak/teleport, dan target cleanup.
