# Upgrade ArenaSystem 0.2.3.1 → 0.2.3.2

1. Hentikan server sepenuhnya dan buat backup world serta `plugins/ArenaSystem`.
2. Hapus JAR ArenaSystem versi lama dari folder `plugins`, lalu pasang hanya `ArenaSystem-0.2.3.2.jar`.
3. Pertahankan `config.yml` dan folder snapshot. Tidak ada perubahan wajib pada konfigurasi.
4. Arena TDM yang sudah ada tidak berubah. Untuk mendapatkan class baru, cukup jalankan update plugin dan pilih class saat persiapan match berikutnya.
5. Layout Relic Rush lama tidak berubah otomatis. Untuk memakai arena yang lebih luas dan memiliki lebih banyak cover, jalankan `/arena regenerate <nama>` pada arena Relic Rush dengan snapshot asli yang valid, atau buat arena Relic Rush baru.
6. Uji di salinan world terlebih dahulu sebelum mengaktifkan pada server utama.

## Perubahan class

- Knight tetap menjadi class seimbang.
- Tank sekarang full diamond, 28 HP (14 hati), shield, dan pedang kayu agar damage lebih rendah.
- Sniper memperoleh Strength I.
- Assassin, Ninja, dan Boxer ditambahkan.
- Max-health base value asli pemain dipulihkan dari PlayerSnapshot setelah match selesai.

## Perubahan Relic Rush

- Spawn RED/BLUE dipindahkan ke ±32 blok dari pusat.
- Sanctum diperbesar menjadi 13x13.
- Tiga jalur utama diperlebar menjadi lima blok, dengan tiga jalur silang.
- Delapan outpost simetris, cover rendah, dan reruntuhan memecah garis pandang.
