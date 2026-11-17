# Patch Notes 0.2.8.2 — Final Death Camera (C3 disable-safety)

Baseline: 0.2.8.1. Hanya jalur pemulihan snapshot yang diubah. Perbaikan C1, C4, C6 tidak disentuh. HUD, gameplay, dan class tidak diubah.

## Temuan
Di 0.2.8.1, saat `onDisable()` (`isEnabled()` false) `restorePlayerSnapshots()` jatuh ke `PlayerSnapshot.restore()` walaupun pemain masih mati. Entri `pendingPlayerRestores` (pemain yang ditunda karena mati) juga tidak diproses saat disable, dan scheduler tidak bisa dipakai. Selain itu `onPlayerRespawn` menghapus entri pending sebelum restore terjadwal berjalan, sehingga disable di antara keduanya bisa menghilangkan snapshot.

## Perubahan
- `ArenaSystemPlugin#restoreSnapshotWithoutScheduler`: bila pemain mati, `spigot().respawn()` dipanggil sinkron; snapshot hanya di-restore bila pemain hidup. Bila masih mati, dicatat peringatan dan restore tidak dijalankan.
- `restorePlayerSnapshots()`: pemain hidup langsung di-restore; pemain mati ditunda (plugin aktif) atau dipulihkan lewat jalur sinkron (plugin sedang disable).
- `onDisable()`: entri `pendingPlayerRestores` milik pemain online diselesaikan lewat jalur sinkron yang sama dan dihapus sebelum restore (tanpa double restore).
- `PlayerArenaListener#onPlayerRespawn`: entri pending dibaca dengan `get`, dan dihapus dengan `remove(key, value)` tepat sebelum restore. Bila gagal, entri tetap untuk respawn/join berikutnya atau `onDisable()`.

## Dasar teknis
Listener plugin tidak dipanggil saat plugin disable (Bukkit melewati plugin yang `isEnabled()` false), jadi respawn paksa di `onDisable()` tidak memicu logika respawn plugin; jika ternyata terpicu, handler keluar lebih awal karena `state.running` false. Asumsi ini berasal dari pengetahuan tentang Bukkit, bukan diuji di server 26.3.

## Batasan yang tetap ada
- Snapshot pemain hanya ada di memori. Entri untuk pemain **offline** tidak bertahan melewati disable/restart (sudah begitu sebelum 0.2.8.x). Menyimpannya ke disk adalah perubahan lebih besar dan tidak dikerjakan.
- Bila pemain masih mati setelah respawn paksa, snapshot-nya tidak dipulihkan (hanya peringatan log).
- Build Maven, kompilasi terhadap Paper 26.3, dan runtime test belum dijalankan.
