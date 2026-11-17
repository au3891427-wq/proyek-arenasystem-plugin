# Catatan refactor — pemisahan ArenaSystemPlugin.java

Refactor ini **tidak mengubah satu baris logic pun**. Semua kode dipindah
apa adanya (copy-paste + penyesuaian pemanggilan `plugin.xxx`), tidak ada
rule baru, tidak ada perilaku yang berubah. Tujuannya murni struktural:
memecah satu file 2.752 baris menjadi beberapa file yang masing-masing
punya satu tanggung jawab, supaya lebih gampang dibaca, direview, dan
diubah tanpa takut menyenggol bagian lain.

## Sebelum

```
ArenaSystemPlugin.java   2.752 baris, satu class merangkap:
  - command router (25+ subcommand, if/else berlapis)
  - 25 @EventHandler (mob, block, explosion, movement, death, relic, portal)
  - 3 inner class (MatchState, PlayerSnapshot, ClassMenuHolder)
  - seluruh logic arena + match + HUD + loadout + proteksi
```

## Sesudah

```
ArenaSystemPlugin.java         1.977 baris — "mesin" arena/match (lifecycle
                                arena, match engine, HUD, loadout, proteksi).
                                Masih besar, lihat "Langkah lanjutan" di bawah.
ArenaCommandExecutor.java        240 baris — parsing & dispatch "/arena ..."
MobProtectionListener.java       106 baris — spawn/gerak/teleport mob di arena
BlockProtectionListener.java     162 baris — break/place/ledakan/api
PlayerArenaListener.java         218 baris — gerak/portal/death/quit/respawn/FF
RelicListener.java               111 baris — siklus item Relic + klik class-selector
ClassMenuListener.java            34 baris — klik menu pilih class TDM
MatchState.java                   51 baris — state satu pertandingan (data class)
PlayerSnapshot.java               78 baris — snapshot inventory/kondisi pemain
ClassMenuHolder.java              19 baris — penanda inventory menu class
```

Semua class baru satu package (`id.hadziq.arenasystem`) dengan `ArenaSystemPlugin`,
jadi tidak perlu import tambahan untuk saling memanggil — cukup package-private.
Field/metode yang sebelumnya `private` di `ArenaSystemPlugin` diubah jadi
package-private (kata `private`-nya dihapus) **hanya supaya file-file baru bisa
memanggilnya**; tidak ada yang jadi `public`, jadi tidak membuka API apa pun
ke luar plugin.

`onEnable()` sekarang mendaftarkan `ArenaCommandExecutor` sebagai executor
`/arena` dan lima listener di atas sebagai pengganti `registerEvents(this, this)`.
`ArenaSystemPlugin` sendiri tidak lagi `implements Listener, CommandExecutor`.

## Langkah lanjutan yang disarankan (belum dikerjakan di sini)

`ArenaSystemPlugin.java` masih ~2.000 baris karena seluruh *business logic*
(arena lifecycle, match engine, HUD, loadout, proteksi) sengaja **tidak**
dipecah lebih jauh di tahap ini — method-method itu saling memanggil sangat
erat (mis. `startMatch` memanggil `buildPreparationLobby`, `clearMobsInArena`,
`updateMatchHud`, `giveClassSelector` yang masing-masing "milik" domain
berbeda), dan tanpa compiler (sandbox ini tidak punya akses Maven/Paper API)
memecahnya lebih jauh berisiko salah referensi yang baru ketahuan saat build
di server kalian. Pemisahan yang aman untuk tahap berikutnya:

- `ArenaEngine` — create/scan/build/delete/regenerate/recover/vegetation/mobs
- `MatchEngine` — join/leave/start/stop/reset/finish/score/relic/preparation
- `LoadoutService` — class kit, relic-runner kit, class menu
- `MatchHudService` — scoreboard sidebar + team marker
- `ProtectionService` — aturan breakable/placeable per arena/global

Pola pemisahannya sama persis seperti yang sudah dilakukan di sini: tiap
service pegang `ArenaSystemPlugin plugin` (atau, lebih baik lagi, pegang map
`arenas`/`matches` miliknya sendiri lalu service lain memintanya lewat
getter) dan dipanggil dengan `plugin.xxx`/`service.xxx`. Rekomendasi:
lakukan satu service per PR, lalu `mvn clean package` + test manual di
server sebelum lanjut ke service berikutnya — karena sandbox ini tidak bisa
compile (tidak ada javac/Maven di lingkungan ini, hanya JRE), jadi verifikasi
nyata harus di mesin kalian dengan JDK 25 + Maven seperti biasa.

## Wajib sebelum dipakai di server

File ini **belum pernah di-compile** (keterbatasan sandbox, sama seperti
dicatat di `VALIDATION_REPORT.md` versi asli). Sebelum dipasang:

1. `mvn clean package` dengan JDK 25 — perbaiki error compile kalau ada
   (kemungkinan terbesar: import yang terlewat/berlebih, semuanya warning
   "unused import", bukan error).
2. Uji semua perintah `/arena ...` di server test, terutama siklus penuh
   create → join → start → (TDM & Relic Rush) → stop/finish → delete, serta
   proteksi break/place dan cleanup vegetasi/mob.
3. Backup world sebelum dipakai di server utama, seperti saran asli di README.
