# Upgrade ArenaSystem 0.2.3.2 → 0.2.3.3

1. Hentikan server Minecraft sepenuhnya.
2. Backup world dan folder `plugins/ArenaSystem` (terutama `config.yml` dan `snapshots`).
3. Build source dengan JDK 25 dan Maven, lalu pasang hanya `ArenaSystem-0.2.3.3.jar`.
4. Jangan hapus `config.yml` atau snapshot lama. Default `class-abilities.*` akan ditambahkan otomatis.
5. Nyalakan server. Uji satu match TDM pada world cadangan sebelum dipakai di server utama.

## Menggunakan skill

Klik kanan item skill di hotbar. Item skill tidak dikonsumsi. Cooldown dibatasi 1–3600 detik, durasi 1–30 detik, dan jarak Shadow Step 2–12 blok. Ninja Smoke Bomb akan berakhir lebih awal saat ia menyerang.

## Durability

Semua senjata, armor, shield, bow/crossbow, serta item skill dari kit TDM dan Relic Runner ditandai Unbreakable. Event pengurangan durability juga dibatalkan selama pemain terdaftar pada match/persiapan. Item asli pemain dipulihkan dari snapshot saat match berakhir.

## Catatan kompatibilitas

- Tidak ada migrasi arena atau snapshot world yang diperlukan.
- Fitur class hanya berlaku di TDM; Relic Rush tetap memakai Relic Runner yang setara untuk kedua tim.
- Jika server memakai plugin lain yang mengubah potion effect, equipment packet, atau atribut pemain, uji interaksi plugin tersebut secara terpisah.
