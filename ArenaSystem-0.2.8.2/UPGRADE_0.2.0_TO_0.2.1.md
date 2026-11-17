# Upgrade 0.2.0 → 0.2.1

1. Hentikan server.
2. Backup world, folder `plugins/ArenaSystem/`, dan `config.yml`.
3. Build source 0.2.1 dengan JDK 25 dan Maven.
4. Hapus JAR 0.2.0 dari folder `plugins`, lalu pasang `ArenaSystem-0.2.1.jar`.
5. Nyalakan server dan pastikan log menampilkan `ArenaSystem 0.2.1 aktif`.
6. Uji mode TDM dan Relic Rush di arena percobaan.

## Perubahan perilaku

- `ArenaGenerator` kini membangun geometri berdasarkan `ArenaData.mode`.
- `/arena create <nama> [seed] [tdm|relic_rush]` memilih desain sejak awal.
- `/arena mode <nama> <tdm|relic_rush>` otomatis memulihkan snapshot kemudian membangun ulang geometri.
- Relic Rush tidak lagi menggunakan menu class maupun loadout class TDM.
- Relic Rush memakai Relic Runner kit yang sama untuk kedua tim, altar tengah, sanctum penyerahan tim, tiga jalur, HUD pembawa Relic, drop handling, dan perlindungan dari item despawn.

Snapshot arena yang sebelumnya dibuat oleh 0.2.0 tetap dipakai. Jangan menghapus folder snapshot atau mengubah nama file snapshot secara manual.
