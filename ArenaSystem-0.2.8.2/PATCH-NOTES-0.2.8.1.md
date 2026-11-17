# Patch Notes 0.2.8.1 — Death Camera Fix

Baseline: 0.2.8.0. Hanya Death Camera dan lifecycle terkait yang diubah. HUD, class, damage, cooldown, dan mekanik match tidak diubah.

## Perbaikan
- **C3 — Kill terakhir.** `restorePlayerSnapshots()` tidak lagi me-restore pemain yang masih `isDead()`. Snapshot disimpan di `pendingPlayerRestores` dan respawn diminta lewat `requestRespawnAfterMatch()`. `onPlayerRespawn()` me-respawn pemain di lokasi snapshot lalu me-restore setelah pemain hidup. Saat plugin dinonaktifkan, jalur lama dipakai (scheduler tidak tersedia).
- **C1 — Kamera tidak mengorbit.** Offset kamera dihitung sekali saat sesi dimulai (`initialDeathCameraLocation`), disimpan sebagai tiga `double` di `DeathCameraSession`, dan dipakai tiap tick (`followCameraLocation`). Yaw/pitch kamera tetap milik viewer.
- **C6 — Kematian berulang.** `onPlayerDeath()` menghentikan sesi follow, membatalkan timer expiry lama, dan menghapus pending lama. Respawn task tidak dibatalkan di sana. `onPlayerRespawn()` mengembalikan peserta match yang masih Spectator ke Survival bila kamera tidak dimulai.
- **C4 — Target spectator.** Handler `PlayerStartSpectatingEntityEvent` membatalkan perpindahan target hanya untuk viewer dengan sesi aktif.
- **C2 — Diaudit.** Guard gerak kini tidak menyentuh event yang hanya berisi rotasi. `setFlySpeed(0f)` tidak dipakai karena belum terbukti perlu.

## Status
Lihat `DEATH-CAMERA-FIX-REPORT.md`. Build Maven dan runtime test belum dijalankan.
