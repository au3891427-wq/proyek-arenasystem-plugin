# Upgrade 0.2.3.4 ke 0.2.3.5

- Ganti JAR lama dengan `ArenaSystem-0.2.3.5.jar`.
- Semua skill aktif sekarang menampilkan cooldown item bawaan seperti Ender Pearl.
- Cooldown visual dimulai setelah skill berhasil digunakan; durasi mengikuti `class-abilities.<skill>.cooldown-seconds` pada `config.yml`.
- Cooldown per-skill tetap divalidasi server-side. Tidak perlu mengubah konfigurasi lama.
- Seluruh perubahan kit, skill, durability, terrain normalizer, dan Sniper pada 0.2.3.4 tetap dipertahankan.
