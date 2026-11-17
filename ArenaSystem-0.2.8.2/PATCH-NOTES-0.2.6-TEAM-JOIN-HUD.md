# ArenaSystem 0.2.6 — Team Join HUD

## Perubahan
- Pemain yang berhasil bergabung ke arena sekarang menerima title HUD.
- Title memakai header `ARENA` dan nama tim berwarna (`RED TEAM`, `BLUE TEAM`, `GREEN TEAM`, atau `YELLOW TEAM`).
- Subtitle menyebutkan nama tim dan arena.
- HUD dikirim kepada pemain yang benar-benar dimasukkan ke tim, termasuk target command admin `/arena join`.
- Pengumuman chat untuk anggota arena lainnya tetap dipertahankan.

## Penyebab sebelumnya
`joinArena()` hanya mengirim pesan chat melalui `sendMessage()`. Fungsi `announce()` juga hanya mengirim chat, sehingga tidak ada title yang ditampilkan saat pemain bergabung.
