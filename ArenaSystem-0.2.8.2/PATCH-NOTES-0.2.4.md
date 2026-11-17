# ArenaSystem 0.2.4 — Team, Random Stuff, Respawn

- `/arena team <nama>` menetapkan 2 tim secara default (RED, BLUE).
- `/arena team <nama> 3` mengaktifkan RED, BLUE, GREEN.
- `/arena team <nama> 4` mengaktifkan RED, BLUE, GREEN, YELLOW.
- Random Stuff berupa satu item khusus yang diaktifkan dengan klik kiri; hadiah lama diganti jika pemain menerima hadiah baru dan item kedaluwarsa setelah 20 detik.
- Respawn TDM membagikan ulang loadout class ke jumlah awal dan menghapus cooldown.
- Death screen menampilkan `Slimed!` dan nama pembunuh; kamera mengikuti pembunuh dalam mode spectator dengan kontrol pandangan bebas.


Catatan tampilan: `Player#sendTitle` digunakan untuk menampilkan pesan kematian pada layar kematian. API Bukkit/Paper tidak menyediakan API untuk menempatkan teks kustom tepat di atas tombol respawn vanilla.
