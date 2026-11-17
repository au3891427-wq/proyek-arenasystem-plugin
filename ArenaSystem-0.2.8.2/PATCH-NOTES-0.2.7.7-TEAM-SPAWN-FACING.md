# Patch Notes — ArenaSystem 0.2.7.7

## Perbaikan arah pandang spawn tim
- Memperbaiki arah hadap saat pemain pertama kali menjalankan `/arena join` untuk tim GREEN dan YELLOW.
- Arah hadap semua tim sekarang dihitung dari posisi spawn aktual menuju titik tengah arena, bukan mengandalkan yaw statis yang dapat tertukar.
- Perhitungan arah yang sama diterapkan pada stasiun lobby persiapan agar orientasi tetap konsisten.
- Pemeriksaan teleport ulang satu tick kemudian kini turut memeriksa yaw dan pitch, bukan hanya jarak posisi.

## Catatan validasi
- Perubahan source diperiksa secara statis dan paket ZIP diuji integritasnya.
- Full Maven build dengan Java 25 dan Paper API 26.3 belum dijalankan di lingkungan ini.
