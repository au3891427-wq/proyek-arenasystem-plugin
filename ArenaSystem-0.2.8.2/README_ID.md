# ArenaSystem 0.2.8.2

Plugin arena multiplayer untuk Paper 26.3 / Java 25. Delapan class TDM memiliki passive unik dan dua skill aktif. Boxer menggunakan kombo Rapid Jab dan Barrage; Demoman menggunakan Pocket Bomb sebagai senjata utama, Dynamite Burst, dan Blast Mine dengan ledakan khusus yang tidak merusak blok arena. **F** mengaktifkan Skill 1, sedangkan **Shift+F** mengaktifkan Skill 2. Klik kanan item skill tetap didukung.


## Kamera Kematian

Ketika pemain terbunuh oleh lawan, kamera spectator dimulai dari posisi di belakang pembunuh. Selama beberapa detik, posisi kamera mengikuti pergerakan pembunuh dengan offset tetap yang dihitung sekali saat sesi dimulai (kamera tidak mengorbit saat pembunuh berputar), sedangkan yaw/pitch kamera tetap bebas dikendalikan pemain yang mati. Input WASD tidak menggeser kamera dari target. HUD/scoreboard match yang sudah aktif tetap dipertahankan.

## Random Stuff

Random Stuff dibagikan sebagai satu item bertag dan hanya aktif lewat klik kanan main hand. Lima reward berfokus pada support (Field Ration, Honey Guard, Fleet Arrow, Aegis Shield, Power Signal), sedangkan empat reward memberi damage (Frost Orb, Ember Shot, Rift Bomb, Duelist Blade). Vanilla action dari material item tetap dibatalkan; proyektil ofensif memakai damage custom dan validasi lawan satu arena/tim.

## Fitur utama

### Mode arena

**Team Deathmatch (`tdm`)**
- Battlefield terbuka dan relatif simetris dengan jalur utama, jalur silang, titik tengah, cover, dan reruntuhan. Markas yang dibuat mengikuti jumlah tim arena: 2 tim (RED/BLUE), 3 tim (RED/BLUE/GREEN), atau 4 tim (RED/BLUE/GREEN/YELLOW).
- Pilihan delapan class TDM selama persiapan: Knight, Tank, Archer, Sniper, Assassin, Ninja, Boxer, dan Demoman.
- Kill lawan memberi 1 poin; friendly kill tidak memberi poin. Target default 20.

**Relic Rush (`relic_rush`)**
- Arena diperlebar: spawn sanctum berada di ±32 blok, tiga jalur utama masing-masing lima blok lebar, dan tiga konektor silang untuk rotasi.
- Delapan outpost/reruntuhan rusak, cover rendah, dan landmark simetris memecah garis pandang tanpa menutup jalur.
- Sanctum tim diperluas menjadi 13x13 blok, sementara altar Relic tetap berada di tengah.
- Tidak memakai menu class TDM. Semua pemain mendapat Relic Runner kit yang sama.
- Pemain membawa Nether Star dari altar ke sanctum tim sendiri dan melakukan delivery dalam radius 8 blok dari titik spawn markas.
- Setiap delivery memberi 1 capture; target default 3. Kill tidak memberi skor.
- Relic yang dibawa pemain akan dijatuhkan ketika carrier mati, disconnect, atau menjatuhkannya secara manual. Item Relic aktif tidak despawn.


## Class abilities (TDM)

Skill dapat digunakan dengan **klik kanan item skill di hotbar**, atau dengan tombol **F** untuk Skill 1 dan **Shift+F** untuk Skill 2. Untuk Shift+F, tahan tombol Sneak atau Sprint sambil menekan F. Item skill diberi tag plugin, tidak dikonsumsi, dan tidak dapat dijatuhkan. Cooldown berlaku per pemain selama match.

| Class | Skill 1 | Skill 2 |
|---|---|---|
| Knight | **Guard** — Resistance II selama 3 detik; cooldown 26 detik | **Rally** — memberi Resistance I kepada rekan tim dalam radius 8 blok selama 4 detik; cooldown 32 detik |
| Tank | **Endurance** — Resistance III selama 5 detik, tetapi gerak melambat; cooldown 45 detik | **Ground Slam** — gelombang kejut radius 5 blok, memberi knockback dan Slowness II selama 2 detik; cooldown 30 detik |
| Assassin | **Burst** — Speed III + Haste II selama 3 detik dan Strength II selama 2 detik; cooldown 30 detik | **Venom Edge** — serangan melee berikutnya memberi Poison II; harus mengenai lawan dalam 8 detik; cooldown 22 detik |
| Ninja | **Smoke Bomb** — Invisibility selama 6 detik; armor dan item juga disembunyikan dari pemain lain. Hilang lebih awal saat menyerang; cooldown 34 detik | **Shadow Step** — blink ke arah pandangan hingga 8 blok, hanya ke lokasi aman; cooldown 20 detik |
| Archer | **Volley** — menembakkan tiga panah sekaligus dan mengonsumsi 3 panah; cooldown 20 detik | **Pinning Shot** — panah berikutnya memperlambat lawan selama 2,5 detik; berlaku 10 detik; cooldown 18 detik |
| Sniper | **Mark Target** — Glowing selama maksimal 8 detik dan target menerima 20% damage tambahan dari lawan; jangkauan 32 blok; cooldown 25 detik | **Focus Mode** — selama 8 detik semua panah mendapat +15% damage dan kecepatan 6.8 blok/tick; cooldown 25 detik |
| Boxer | **Rapid Jab** — rangkaian hingga tiga pukulan cepat ke arah depan, masing-masing 1,15 damage; jangkauan 3,4 blok; cooldown 20 detik | **Barrage** — hingga enam pukulan cepat pada target awal, masing-masing 0,90 damage; rangkaian berhenti jika target keluar dari jarak/garis pandang; cooldown 28 detik |
| Demoman | **Dynamite Burst** — maksimal tiga proyektil diluncurkan berurutan dengan jeda 0,2 detik; setiap proyektil memiliki fuse 1,35 detik, maksimum 4,8 damage sebelum mitigasi, dan knockback lebih rendah; ledakan tidak merusak blok; cooldown default 10 detik | **Blast Mine** — ranjau aktif saat lawan mendekat dalam radius 2,25 blok; radius ledakan 3,5 blok; aktif 20 detik; maksimal satu ranjau; penanda Creeper hanya terlihat oleh pemilik; cooldown default 20 detik |

**Senjata utama Demoman:** Pocket Bomb (TNT Minecart) dilempar dengan klik kanan sebagai bom tunggal custom; maksimum 3,4 damage sebelum mitigasi, radius 3 blok, cooldown default 2 detik. Proyektil tidak membuat ledakan vanilla dan tidak merusak blok arena. Dynamite Burst tetap menjadi skill 1 yang lebih kuat dengan tiga bom.

**Heavy Jab Boxer:** ketika memakai tangan kosong, klik kanan ke arah lawan dalam jarak 3,4 blok untuk memberi serangan 2,6 damage dan knockback; cooldown 2,1 detik.

**Mark Sniper:** pemain yang terkena Mark mendapat pesan bahwa dirinya sedang ditandai, termasuk nama Sniper yang menandainya. Selama maksimal 8 detik atau sampai tiga panah Sniper mengenai target, serangan pemain lawan memberikan 20% damage tambahan. Q mengaktifkan AIM LOCK ke target tersebut; lock dibatalkan jika target atau pemilik mark tidak lagi valid, keluar dari match/arena, mati, atau durasinya habis.

Sniper menggunakan 32 Arrow of Harming I. Panah Sniper melaju dengan kecepatan **6.2 blok/tick** (normal), meningkat menjadi **6.8 blok/tick** saat Focus Mode; gravitasi dinonaktifkan agar lintasannya tetap lurus; panah class lain tetap mengikuti lintasan normal.

Assassin dan Ninja mendapat Haste I pasif. Assassin mempunyai attack speed dasar 5.5 (default 4.0) sebagai bonus spesialis. Boxer mendapat Strength I, Speed I, Haste I, serta attack speed dasar 6.0 melalui atribut `ATTACK_SPEED` (bukan hanya mengandalkan potion). Setelah serangan ambush Shadow Veil Ninja berhasil, lima serangan berikutnya menggunakan attack speed 20.0; serangan pemicu tidak dihitung dan serangan yang meleset tidak mengurangi hit. Nilai dasar attack speed disimpan dan dikembalikan setelah efek follow-up selesai atau state pemain dibersihkan.

Durasi/cooldown/jarak yang didukung konfigurasi dapat diubah di `plugins/ArenaSystem/config.yml` pada bagian `class-abilities`. Untuk mencegah konfigurasi ekstrem, durasi dibatasi 1–30 detik, cooldown 1–3600 detik, dan jarak Shadow Step 2–12 blok. Contoh:

```yaml
class-abilities:
  ninja_vanish:
    duration-seconds: 6
    cooldown-seconds: 34
  ninja_shadow_step:
    distance-blocks: 8
    cooldown-seconds: 20
  tank_endurance:
    duration-seconds: 5
    cooldown-seconds: 45
```

## Passive class (TDM)

| Class | Passive | Efek utama |
|---|---|---|
| Knight | **Guard** | Mengurangi 6% damage dari pemain lawan. |
| Archer | **Steady Aim** | Diam 1 detik sebelum menembak memberi panah +10% damage. |
| Sniper | **Precision** | Panah yang mengenai lawan dari jarak minimal 16 blok mendapat +10% damage. |
| Tank | **Bulwark** | Mengurangi damage 5%; peluang 15% menyerap setengah damage tersisa, dengan internal cooldown 3,5 detik. |
| Assassin | **Executioner** | Serangan melee mendapat +12% damage jika HP lawan tersisa maksimal 35%. |
| Ninja | **Shadow Veil** | Sneak 5,6 detik tanpa menyerang/terkena serangan memberi Speed II dan vanish penuh selama 5,3 detik. Serangan pertama yang berhasil memberi +20% damage. |
| Boxer | **Rhythm** | Setiap 3 basic attack tanpa senjata yang berhasil mengenai lawan mengurangi cooldown Rapid Jab hingga 4,5 detik. Hit skill tidak dihitung. |
| Demoman | **Demolition Expert** | Mengurangi recoil ledakan Dynamite milik sendiri: 30% horizontal dan 20% vertikal. |

Dynamite dan Blast Mine menggunakan ledakan entity-only khusus. Keduanya tidak memanggil ledakan TNT/vanilla dan tidak mengubah blok, terrain, atau struktur arena. Skill hanya memengaruhi lawan di pertandingan TDM yang sama; rekan tim dan pemain di luar match tidak terkena.

### Durability protection

Semua item yang dibagikan oleh kit TDM dan Relic Runner ditandai `Unbreakable`, termasuk senjata, armor, shield, bow/crossbow, dan item skill. Event pengurangan durability juga dibatalkan selama pemain terdaftar di antrean/persiapan/pertandingan arena. Item kit tidak kehilangan durability. Kondisi inventory asli pemain dipulihkan dari snapshot setelah match.

## Terrain normalizer dan detector

Saat `/arena create`, plugin memindai heightmap 81x81 lalu menjalankan normalisasi sebelum membuat struktur arena.

- Batas beda tinggi antarkolom tetangga mengikuti `terrain.max-adjacent-step` (default 2; nilai config dibatasi 1–4).
- Elevasi dua area spawn RED dan BLUE diseimbangkan ke tinggi yang sama. Area pad 9x9 masing-masing tim dibuat rata dan kontur di sekelilingnya dibaurkan menjadi ramp.
- Normalisasi diterapkan secara fisik: kolom rendah di-*fill* dengan DIRT dan GRASS_BLOCK; kolom tinggi di-*cut* dan ditutup GRASS_BLOCK.
- Generator TDM maupun Relic Rush membangun semua jalur, pad, altar, cover, dan perimeter di atas profil hasil normalisasi.
- `TerrainDetector` memeriksa rentang elevasi, lonjakan antarkolom, kekasaran permukaan, keseimbangan spawn, dan jalur utama. Terrain yang masih di luar ambang keselamatan ditolak sebelum world diubah.
- Arena ditolak jika perubahan cut/fill satu kolom melewati `terrain.max-column-adjustment` (default 12 blok).

Contoh konfigurasi:

```yaml
terrain:
  max-adjacent-step: 2
  max-column-adjustment: 12
```

Semua edit cut/fill dan struktur arena digabung ke satu rencana blok. Snapshot asli disimpan sebelum blok world disentuh. Bila normalisasi akan menyentuh TileState seperti chest, spawner, atau sign dengan data, pembuatan dibatalkan karena format snapshot BlockData plugin ini tidak menyimpan data block entity.

Detail algoritme, ambang detector, dan pengujian normalizer ada di `TERRAIN-NORMALIZER.md` dan `TERRAIN-DETECTOR.md`.

## Vegetation cleanup dan proteksi

- Setelah arena dibuat, plugin membersihkan rumput, fern, bunga, semak, vine, dan tanaman pengganggu lain di area arena.
- Cleanup memperbarui snapshot asli sebelum menghapus tanaman, sehingga `delete`, `regenerate`, `mode`, dan recovery dapat memulihkan terrain awal.
- Arena lama mendapat satu kali cleanup migrasi saat plugin dimuat jika snapshot pemulihan tersedia.
- Tanaman pengganggu boleh dihancurkan pemain secara default walaupun proteksi blok arena aktif. Aturan `breakable`/`disbreakable` eksplisit tetap memiliki prioritas.
- Pengaturan: `protection.allow-vegetation-break-by-default: true`.
- Admin dapat menjalankan `/arena clearvegetation <nama>` atau `/arena <nama> clearvegetation`. Pertandingan harus dihentikan dahulu dan snapshot pemulihan wajib tersedia.

## Membuat arena

```text
/arena create <nama> [seed] [tdm|relic_rush]
```

Contoh:

```text
/arena create war tdm
/arena create relic relic_rush
/arena create relic-2 123456 relic_rush
/arena create duel 123456 tdm
```

Jika mode tidak ditulis, default adalah `tdm`.

## Mengubah mode atau regenerate arena

```text
/arena mode <nama> <tdm|relic_rush>
/arena regenerate <nama> [seed]
```

Perintah mode/regenerate memulihkan snapshot terrain asli terlebih dahulu, memindai ulang medan, lalu menormalisasi dan membangun arena kembali. Perintah membutuhkan snapshot asli yang valid. Jika snapshot tidak tersedia (misalnya sebagian arena legacy), perubahan ditolak agar world tidak ditimpa tanpa pemulihan aman. Backup world tetap disarankan sebelum perubahan besar.

## Siklus pertandingan

```text
/arena join <nama> red
/arena join <nama> blue
/arena start <nama>
/arena stop <nama>
/arena reset <nama>
/arena leave
```

Admin dapat memasukkan pemain lain:

```text
/arena join <nama> red <namaPlayer>
```

## Perintah manajemen

```text
/arena list
/arena info <nama>
/arena tp <nama> red|blue|green|yellow|center
/arena delete <nama> confirm
/arena recover <nama>
/arena clearmobs <nama>
/arena clearvegetation <nama>
```

Perintah proteksi:

```text
/arena <nama> breakable|disbreakable|placeable|displaceable <op|@s|@a|player>
```

## Upgrade dari 0.2.8.1 ke 0.2.8.2

1. Hentikan server dan buat backup world serta folder `plugins/ArenaSystem`.
2. Ganti `ArenaSystem-0.2.8.1.jar` dengan `ArenaSystem-0.2.8.2.jar` setelah build berhasil.
3. Tidak ada perubahan config. Patch ini hanya memperbaiki pemulihan snapshot pemain yang masih mati saat plugin dinonaktifkan. Lihat `PATCH-NOTES-0.2.8.2.md`. Build Maven dan runtime test belum dijalankan (lihat `BUILD-ATTEMPT-0.2.8.2.log`).

## Upgrade dari 0.2.8.0 ke 0.2.8.1

1. Hentikan server dan buat backup world serta folder `plugins/ArenaSystem`.
2. Ganti `ArenaSystem-0.2.8.0.jar` dengan `ArenaSystem-0.2.8.1.jar` setelah build berhasil.
3. Tidak ada perubahan config; `config.yml` dan folder `snapshots` dipertahankan.
4. Patch ini hanya memperbaiki Death Camera dan lifecycle kill terakhir. Lihat `PATCH-NOTES-0.2.8.1.md` dan `DEATH-CAMERA-FIX-REPORT.md`. Build Maven dan runtime test belum dijalankan di lingkungan penyusunan (lihat `BUILD-ATTEMPT-0.2.8.1.log`); jalankan `mvn clean package` dengan JDK 25 lalu uji di server Paper 26.3 cadangan.

## Upgrade dari 0.2.7.9 ke 0.2.8.0

1. Hentikan server dan buat backup world serta folder `plugins/ArenaSystem`.
2. Ganti `ArenaSystem-0.2.7.9.jar` dengan `ArenaSystem-0.2.8.0.jar` setelah build berhasil.
3. Pertahankan `config.yml` dan folder `snapshots`; update ini tidak membutuhkan penghapusan data lama.
4. Uji death camera (rotasi bebas, posisi tetap mengikuti killer, HUD tetap terlihat) dan seluruh reward Random Stuff di server Paper 26.3 cadangan sebelum deploy.

## Upgrade dari 0.2.3.5

1. Hentikan server dan backup world serta folder `plugins/ArenaSystem`.
2. Ganti JAR lama dengan `ArenaSystem-0.2.3.7-5.jar`; jangan muat dua JAR ArenaSystem sekaligus.
3. Pertahankan `config.yml` dan folder `snapshots` lama. Tidak ada opsi konfigurasi baru yang wajib ditambahkan.
4. Setelah restart, skill dapat diaktifkan dengan klik kanan di udara/area kosong maupun saat menghadap blok. Rapid Jab/Barrage harus diarahkan ke lawan; Dynamite dilempar ke arah pandangan, sedangkan Blast Mine dipasang pada permukaan padat di dalam arena.
5. Uji pada salinan world sebelum digunakan di server utama.

## Build

Persyaratan: JDK 25 dan Apache Maven.

Dari folder yang berisi `pom.xml`:

```powershell
mvn clean package
```

JAR output:

```text
target\ArenaSystem-0.2.8.2.jar
```

Atau jalankan `build.bat` / `build.ps1`.

## Status validasi

Algoritme normalizer dapat dikompilasi terpisah dan lulus tes terrain datar, tebing 12/20 blok, peak tunggal 20 blok, serta 250 heightmap acak untuk masing-masing offset spawn ±27 dan ±32. Patch 0.2.8.0 belum dapat dikompilasi atau diuji pada server Paper di lingkungan penyusunan ini: tersedia JDK 21, tidak ada Maven, dan dependensi Paper tidak tersedia lokal. Proyek menetapkan Java 25 dan Paper API 26.3. Lihat `VALIDATION_REPORT-0.2.8.0.md` untuk hasil pemeriksaan source, kegagalan build yang sebenarnya, dan checklist runtime. Jalankan `mvn clean package` dengan JDK 25, lalu lakukan pengujian pada world cadangan.


## Cooldown skill (0.2.3.5)

Setelah skill berhasil digunakan, item pemicunya menampilkan overlay cooldown radial seperti Ender Pearl. Durasi mengikuti `class-abilities.<skill>.cooldown-seconds` pada `config.yml`. Jika skill gagal (misalnya tidak ada target atau lokasi Shadow Step tidak aman), cooldown tidak dimulai.


## Kontrol skill
- Tekan **F** (swap hands bawaan Minecraft) untuk Skill 1 saat TDM berlangsung. Pertukaran offhand dibatalkan agar item tidak berpindah.
- Tekan **Shift+F** (atau Sprint+F) untuk Skill 2. Ninja menggunakan kombinasi ini untuk **Shadow Step**; class lain menjalankan Skill 2 masing-masing.
- Klik kanan item skill tetap tersedia sebagai cara alternatif untuk mengaktifkan skill yang sesuai.
- Smoke Bomb menggunakan `hidePlayer` per viewer; armor dan semua equipment tidak terlihat selama efek.
- Panah Archer Volley tidak dapat dipungut dan dihapus setelah 5 detik.


### Pengaturan jumlah tim TDM

- `/arena team <nama> [2|3|4]` mengatur jumlah tim Team Deathmatch. Tanpa angka, default adalah 2 tim (RED dan BLUE); angka 3 menambahkan GREEN; angka 4 menambahkan YELLOW. Atur sebelum pemain masuk antrean.
- Relic Rush tetap memakai 2 tim karena objektif sanctum-nya hanya memiliki dua markas.


## Random Stuff dan respawn (0.2.4)
- Random Stuff diberikan sebagai satu item khusus yang digunakan dengan klik kanan.
- Item yang tidak digunakan hilang setelah 20 detik; hadiah baru menggantikan hadiah lama yang belum dipakai.
- Setelah respawn TDM, seluruh loadout class dipulihkan ke jumlah awal dan cooldown direset.
- Jika dibunuh lawan, layar kematian menampilkan `Slimed!` dan nama pembunuh; kamera spectator mengikuti pembunuh dan pemain dapat menggerakkan pandangan selama kamera aktif.
