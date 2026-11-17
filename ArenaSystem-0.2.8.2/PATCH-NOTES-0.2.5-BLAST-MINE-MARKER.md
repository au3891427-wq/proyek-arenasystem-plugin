# ArenaSystem 0.2.5 — Blast Mine Marker Fix

## Perbaikan
- Creeper penanda Blast Mine diberi scoreboard tag internal khusus.
- `MobProtectionListener` sekarang mengecualikan hanya entity dengan tag tersebut dari pembatalan spawn, pembatasan masuk arena, pembatasan teleport, targeting cleanup, dan pembersihan mob saat chunk dimuat.
- Mob biasa tetap ditolak/dibersihkan dari footprint arena.
- Output artifact Maven dan versi plugin diselaraskan ke `0.2.5`.

## Validasi
- Pemeriksaan sumber memastikan marker diberi tag sebelum spawn dan seluruh jalur cleanup mob menghormati tag itu.
- ZIP dapat diperiksa integritasnya. Build Paper penuh tetap memerlukan JDK 25 dan dependency Paper API yang sesuai.
