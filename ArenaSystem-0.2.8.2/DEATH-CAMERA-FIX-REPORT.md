# Death Camera Fix Report — 0.2.8.1

Status memakai label: SRC = diperiksa dari source; COMPILE = dikompilasi; RUNTIME = diuji di server.

## Root cause dan perbaikan
**C1 (kamera mengorbit).** `deathCameraLocation()` memakai `target.getDirection()` milik killer setiap tick, jadi offset ikut berputar bersama yaw killer. Kini offset dihitung sekali di `startDeathCamera()` dari `initialDeathCameraLocation()` (rumus lama: 3,25 blok di belakang, +1,48 tinggi, pitch 6°, nilai tidak diubah) dan disimpan sebagai `offsetX/Y/Z` (primitif, tidak ada Vector/Location mutable yang disimpan). `tickDeathCamera()` memakai `followCameraLocation()`: `killer.getLocation()` (salinan baru) + offset, dengan yaw/pitch viewer. Status: FIXED, SRC; belum COMPILE, belum RUNTIME.

**C3 (kill terakhir TDM).** Rantai `onPlayerDeath → beginDeathCamera → addScore → finishMatch → stopAndRestoreMatch → stopDeathCamerasForArena` membatalkan respawn task, lalu `restorePlayerSnapshots()` memanggil `PlayerSnapshot.restore()` pada pemain yang masih mati. Kini: pemain `isDead()` tidak di-restore; snapshot dititipkan ke `pendingPlayerRestores` (map yang sudah ada dan sudah dibersihkan oleh join), `requestRespawnAfterMatch()` meminta respawn satu kali lewat `spigot().respawn()` (pola yang sama dengan respawn lama, dijaga `isDead()`), dan `onPlayerRespawn()` mengambil snapshot lebih dulu, mengatur lokasi respawn ke lokasi snapshot, lalu `restore()` satu tick setelah respawn. Pemain yang keluar sebelum respawn tetap dipulihkan saat join. Penanganan saat `onDisable()` diganti di 0.2.8.2 (lihat `PATCH-NOTES-0.2.8.2.md`). Status: FIXED, SRC; belum COMPILE, belum RUNTIME. Efek `teleport`/`setHealth` pada pemain mati di Paper tidak diuji; desain ini menghindarinya.

**C6 (kematian berulang).** `onPlayerDeath()` hanya menghentikan follow. Kini juga membatalkan `deathCameraTasks` (expiry) dan menghapus `pendingDeathCameras` lama lewat helper `cancelDeathCameraExpiry()`. Respawn task tidak disentuh di sana. `stopDeathCameraFollow()` idempotent dan mengosongkan `followTask`. Status: FIXED, SRC.

**C4 (target spectator).** `PlayerStartSpectatingEntityEvent` (com.destroystokyo.paper.event.player) dikonfirmasi ada di javadoc Paper/Folia 26.1.2 dan 26.2; javadoc 26.3 tidak diperiksa dan belum dikompilasi terhadap 26.3. Handler membatalkan event hanya bila viewer ada di `activeDeathCameras`. `PlayerArenaListener` terdaftar sekali (satu `new` di `ArenaSystemPlugin`). Status: FIXED, SRC; menunggu COMPILE dan RUNTIME.

**C2 (rubberband/snap-back).** Audit: `PlayerMoveEvent` HIGHEST hanya memblokir posisi dan meneruskan yaw/pitch; teleport kamera tidak memicu `PlayerMoveEvent` sehingga tidak diblokir guard; tidak ada handler `PlayerTeleportEvent` di proyek; `RelicListener` melewati non-Survival; `ClassPassiveManager` (MONITOR) hanya membaca. `TeleportFlag.Relative` tidak punya flag rotasi relatif, jadi rotasi pada teleport selalu absolut. Perubahan: event yang hanya mengubah rotasi tidak lagi disentuh. `setFlySpeed(0f)` tidak dipakai. Status: stabilitas jaringan dan rotasi NOT VERIFIED (RUNTIME).

## File diubah
- `src/main/java/id/hadziq/arenasystem/PlayerArenaListener.java`
- `src/main/java/id/hadziq/arenasystem/ArenaSystemPlugin.java` (`restorePlayerSnapshots`, string versi)
- `pom.xml`, `src/main/resources/plugin.yml`, `build.bat`, `build.ps1`, `README_ID.md`, `GEMINI.md`, `CHANGELOG.md`, `STATE_REGISTRY.md`
- Baru: `PATCH-NOTES-0.2.8.1.md`, `DEATH-CAMERA-FIX-REPORT.md`, `BUILD-ATTEMPT-0.2.8.1.log`

## Dampak lifecycle
- Pemain mati saat match berakhir kini selalu respawn di lokasi snapshot dan dipulihkan sekali; tidak ada pending yang tertinggal selain untuk pemain offline (pola lama).
- Camera follow, expiry, dan respawn request tetap tiga lifecycle terpisah.
- Tidak ada scheduler baru yang berjalan terus-menerus.

## Validasi
- SRC: ditinjau manual. `javac` terhadap dua file yang diubah tidak melaporkan error sintaks; hanya error simbol karena API Paper tidak tersedia. Ini bukan bukti kompilasi.
- COMPILE: tidak dilakukan. Lingkungan hanya JDK 21 (proyek butuh 25), `repo.papermc.io` dan Maven Central tidak terjangkau. Log `mvn clean package` yang gagal ada di `BUILD-ATTEMPT-0.2.8.1.log`. Tidak ada JAR yang dibuat.
- Test otomatis: tidak ada (proyek tidak punya test source untuk kode ini).
- RUNTIME: tidak dilakukan.

## Belum dilakukan / belum terverifikasi
Build Maven; semua runtime test (follow, putaran 360°, free look, WASD, killer mati/disconnect, kill terakhir GREEN/YELLOW/RED/BLUE, Relic Rush, banyak viewer, plugin disable, HUD). Kemungkinan jitter dan snap-back rotasi tetap belum terbukti maupun terbantahkan.
Temuan C5 dan C7 dari audit tidak dikerjakan (tidak diminta).

## Checklist runtime
1. Dua pemain: korban melihat killer dari belakang, kamera mengikuti.
2. Killer berputar 90/180/360°: kamera tidak berpindah sisi.
3. Viewer memutar mouse saat killer berjalan: rotasi tidak tertarik balik.
4. Viewer menekan W/A/S/D/Space/Shift: posisi tetap mengikuti killer.
5. Killer mati / disconnect: kamera diam, timer habis, respawn normal.
6. Viewer mati lagi sebelum respawn (mis. `/kill`): tidak ada task ganda, tidak tertinggal Spectator.
7. Kill terakhir TDM (RED, BLUE, GREEN, YELLOW): korban respawn di lokasi snapshot, inventory/gamemode/health pulih, HUD asli kembali.
8. Kill terakhir Relic Rush / finish saat ada pemain mati.
9. Klik kiri pemain lain dan menu teleport spectator: kamera tidak terlepas.
10. Beberapa viewer pada satu killer: rotasi independen.
11. `/arena stop`/`reset` dan plugin disable dengan kamera aktif: Survival kembali, tidak ada task tersisa.
12. HUD utuh selama spectator dan setelah respawn.

## Addendum 0.2.8.2
Lihat `PATCH-NOTES-0.2.8.2.md`: restore saat plugin disable kini aman untuk pemain mati (respawn sinkron lalu restore), pending snapshot diproses di `onDisable()`, dan entri pending dihapus tepat sebelum restore. Status: FIXED di level source; belum COMPILE, belum RUNTIME.
