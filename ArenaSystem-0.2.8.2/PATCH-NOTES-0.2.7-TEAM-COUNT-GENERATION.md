# ArenaSystem 0.2.7 — Team-count Arena Generation

## Perubahan
- Jumlah markas TDM saat generate/regenerate kini mengikuti `arenas.<nama>.team-count`.
- Default 2 tim hanya membangun markas RED dan BLUE.
- 3 tim membangun RED, BLUE, dan GREEN.
- 4 tim membangun RED, BLUE, GREEN, dan YELLOW.
- Regenerate memulihkan snapshot layout lama terlebih dahulu, sehingga markas yang dinonaktifkan kembali ke blok terrain asli.
- Lobby preparation hanya menampilkan zona dan penanda bagi tim yang aktif.
- `/arena info <nama>` menampilkan tim aktif; `/arena tp` menolak tujuan markas yang tidak diaktifkan.
- Setelah `/arena team <nama> [jumlah]`, jalankan `/arena regenerate <nama>` untuk menerapkan layout baru.

## Penggunaan
- `/arena team arena` → RED, BLUE.
- `/arena team arena 3` → RED, BLUE, GREEN.
- `/arena team arena 4` → RED, BLUE, GREEN, YELLOW.
- `/arena regenerate arena` → membangun ulang markas sesuai pilihan terakhir.

## Pemeriksaan
- Source dicek untuk memastikan generator menerima konfigurasi team-count dan pemanggilnya meneruskan nilai itu.
- Uji kompilasi penuh belum dijalankan pada lingkungan ini karena target proyek Java 25/Paper API.
