# Upgrade ArenaSystem 0.2.3.6 → 0.2.3.7

1. Matikan server dengan `stop`.
2. Backup folder `plugins/ArenaSystem`, data arena, dan snapshot.
3. Ganti JAR lama dengan `ArenaSystem-0.2.3.7.jar`; jangan memasang dua JAR ArenaSystem sekaligus.
4. Nyalakan server dan uji skill di pertandingan TDM.

## Kontrol
- **F** (swap hands bawaan Minecraft): skill utama class. Pertukaran offhand dibatalkan selama pertandingan TDM.
- **Shadow Step** tetap dapat digunakan dari item skill untuk saat ini. Tombol **R** khusus tidak dapat dibaca oleh plugin Paper murni karena client vanilla tidak mengirim event R ke server; dibutuhkan mod/client companion untuk keybind R yang sebenarnya.

## Perubahan Smoke Bomb
Ninja disembunyikan dari viewer menggunakan API `hidePlayer`, sehingga armor dan seluruh equipment tidak terlihat. Entity ditampilkan kembali saat efek selesai atau dibatalkan.

## Archer Volley
Panah skill tidak dapat dipungut dan dihapus otomatis setelah 5 detik.
