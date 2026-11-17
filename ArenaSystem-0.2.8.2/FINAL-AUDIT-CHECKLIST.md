# ArenaSystem 0.2.3.9 — Pemeriksaan Mandiri

Semua langkah utama di bawah dapat dilakukan dengan satu akun OP. Tidak perlu menyiapkan pemain kedua. Untuk interaksi yang memang menargetkan pemain lawan, bagian tersebut ditandai sebagai perilaku PvP dan tidak dianggap gagal hanya karena tidak ada lawan online.

## Terrain dan pemulihan
- [ ] Buat arena TDM di area yang memiliki short grass, tall grass, fern, dan flowers. Periksa permukaan arena serta lereng bertingkat.
- [ ] Buat arena Relic Rush pada area dengan vegetasi serupa.
- [ ] Muat arena lama yang masih menyimpan flag `combat-vegetation-cleaned-v3`; pastikan pemindaian versi v4 berjalan.
- [ ] Jalankan `/arena clearvegetation <arena>`, periksa jumlah blok yang dihapus, lalu gunakan pemulihan arena dan pastikan vegetasi asli kembali dari snapshot.
- [ ] Pastikan leaves, logs, grass blocks, dan struktur arena tidak ikut terhapus.

## Lobby TDM dan tampilan
- [ ] Atur `/arena team <arena> 2`, bergabung ke RED dengan `/arena join <arena> red`, lalu mulai dengan `/arena start <arena>`.
- [ ] Pastikan menu pemilihan class tersedia di lobby persiapan.
- [ ] Pastikan suara note block muncul pada detik 5, 4, 3, dan 2; detik 1 memakai suara mulai.
- [ ] Saat pertandingan dimulai, title hanya menampilkan `ARENA / Teamdeathmatch / GO!`.
- [ ] Pastikan scoreboard memakai tampilan pertandingan biasa, tanpa label mode pemain tunggal atau pesan tambahan.
- [ ] Jalankan hingga timer selesai. Dengan satu pemain, sistem tidak menampilkan klaim menang/kalah karena tidak ada tim lawan; hasil akhir tetap menampilkan waktu dan skor.
- [ ] Pastikan inventory, scoreboard sebelumnya, lokasi, dan perubahan blok dipulihkan setelah pertandingan.

## Jumlah tim
- [ ] Saat tidak ada pertandingan aktif, atur `/arena team <arena> 3`; pastikan HUD mencantumkan RED, BLUE, dan GREEN.
- [ ] Gunakan `/arena tp <arena> green` untuk memeriksa spawn GREEN.
- [ ] Atur `/arena team <arena> 4`; pastikan HUD mencantumkan RED, BLUE, GREEN, dan YELLOW.
- [ ] Gunakan `/arena tp <arena> yellow` untuk memeriksa spawn YELLOW.
- [ ] Pastikan RED/BLUE tetap menjadi konfigurasi default dan Relic Rush tetap dua tim.

## Class dan sistem pertandingan
- [ ] Pilih seluruh class satu per satu. Pastikan menu, loadout, item skill, cooldown, dan pemulihan inventory berjalan.
- [ ] Demoman: periksa tiga proyektil Dynamite, efek ledakan, Creeper marker yang diam dan glowing, serta penghapusan marker saat kedaluwarsa atau class/match dibersihkan. Kontak mine dan notifikasi mengenai pemain lawan memerlukan target pemain lawan.
- [ ] Boxer: periksa Heavy Jab dengan tangan kosong dan cooldown 3 detik, Rapid Jab cooldown 20 detik, serta tampilan Barrage. Bonus jab ketiga dan efek pada target memerlukan target pemain yang sesuai.
- [ ] Assassin: pastikan Golden Sword ada di loadout dan menu.
- [ ] Knight: periksa item Power Strike dan cooldown Skill 1.
- [ ] Tank: periksa aktivasi Ground Slam, animasi/partikel, dan cooldown. Damage serta efek pada Ninja yang menghilang memerlukan pemain target.
- [ ] Sniper: periksa item Mark, Q sebagai toggle aim lock, dan indikator/durasi mark. Pergerakan aim dan hit-count tiga panah membutuhkan pemain lawan.
- [ ] Random Stuff: percepat durasi pertandingan melalui config bila perlu. Dengan satu pemain yang memenuhi syarat, satu item diberikan setiap interval 30 detik.

## Relic Rush
- [ ] Mulai Relic Rush dengan satu pemain di RED. Pastikan relic muncul di altar dan dapat diambil melalui proximity.
- [ ] Bawa relic ke sanctum BLUE untuk memastikan delivery terdeteksi.
- [ ] Periksa carrier movement penalty, efek glow, dan pemulihan status setelah pertandingan dihentikan.

## Batas pemeriksaan satu akun
- [ ] Perilaku yang mensyaratkan pemain lawan—damage PvP, kill score, mark pada pemain, trigger Blast Mine oleh lawan, notifikasi pemilik mine, serta title pemenang/kalah—tidak dapat diamati dengan satu akun tanpa target pemain tambahan. Fitur tersebut tetap memakai alur pertandingan normal dan tidak diberi pesan mode khusus.
