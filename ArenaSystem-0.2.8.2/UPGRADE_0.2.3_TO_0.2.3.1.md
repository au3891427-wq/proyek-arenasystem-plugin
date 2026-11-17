# Upgrade ArenaSystem 0.2.3 → 0.2.3.1

1. Matikan server.
2. Backup world, `plugins/ArenaSystem/config.yml`, dan `plugins/ArenaSystem/snapshots/`.
3. Ganti JAR 0.2.3 dengan `ArenaSystem-0.2.3.1.jar`; pastikan hanya satu JAR ArenaSystem berada di folder plugins.
4. Jangan hapus atau mengganti folder snapshot. Format snapshot tidak berubah.
5. Jalankan server. Default `terrain.max-adjacent-step` dan `terrain.max-column-adjustment` akan ditambahkan ke config lama secara otomatis.
6. Arena yang sudah ada tetap berada pada terrain/layout saat ini; plugin tidak menormalisasinya otomatis saat startup.
7. Untuk menerapkan normalizer ke arena yang sudah ada, gunakan `/arena regenerate <nama>` atau `/arena mode <nama> <tdm|relic_rush>`. Operasi memerlukan snapshot original yang valid.
8. Uji pada salinan world terlebih dahulu. Jika pemindaian mendeteksi TileState di area yang perlu di-cut/fill, pembuatan ditolak demi mencegah hilangnya data block entity.

Proyek membutuhkan JDK 25 dan Maven. Build dengan `mvn clean package`.
