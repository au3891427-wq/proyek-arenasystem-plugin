# Upgrade ArenaSystem 0.2.2 → 0.2.3

1. Backup world utama dan folder `plugins/ArenaSystem/` terlebih dahulu.
2. Hentikan server.
3. Ganti source proyek dengan paket 0.2.3, atau terapkan perubahan source yang sesuai.
4. Jalankan `mvn clean package` dengan JDK 25.
5. Hapus JAR ArenaSystem versi lama dari folder `plugins`, lalu pasang `ArenaSystem-0.2.3.jar`.
6. Nyalakan server. Arena yang sudah ada akan mendapat satu kali cleanup vegetasi jika snapshot aslinya tersedia.

## Perubahan perilaku

- Arena baru membersihkan tanaman pengganggu setelah generator selesai.
- Snapshot asli diperbarui sebelum tanaman dihapus; `delete` dan `regenerate` tetap bisa memulihkannya.
- Pemain boleh menghancurkan tanaman pengganggu secara default meskipun blok arena lain dilindungi.
- Aturan `breakable`/`disbreakable` eksplisit tetap mengalahkan default tersebut.
- Gunakan `/arena clearvegetation <nama>` untuk membersihkan ulang arena secara manual. Perintah menolak berjalan saat match aktif atau ketika snapshot tidak tersedia.

Jika arena tidak memiliki snapshot pemulihan, plugin tidak akan membersihkan tanaman secara otomatis karena tidak dapat menjamin restorasi world yang aman. Buat backup dan regenerate arena di lokasi aman terlebih dahulu.
