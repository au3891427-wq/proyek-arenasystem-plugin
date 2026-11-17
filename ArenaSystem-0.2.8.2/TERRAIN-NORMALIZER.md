# Terrain Normalizer — ArenaSystem 0.2.3.3

## Alur

Sebelum generator arena dijalankan, heightmap 81x81 yang dipindai oleh `TerrainScanner` diteruskan ke `TerrainNormalizer`. Normalizer membuat profil baru dan tidak mengubah world secara langsung.

Default konfigurasi:

```yaml
terrain:
  max-adjacent-step: 2
  max-column-adjustment: 12
```

`max-adjacent-step` dibatasi 1–4 blok. Normalizer menerapkan batas tersebut pada setiap pasangan kolom tetangga, menyeimbangkan titik spawn RED/BLUE ke elevasi sama, dan menjaga area pad 9x9 masing-masing tim tetap rata. Kontur sekitar pad mengikuti ramp berbasis jarak. Offset spawn TDM adalah ±27; arena Relic Rush baru memakai ±32 agar sanctum lebih jauh dari altar. Offset arena lama disimpan di config dan tetap dipakai sampai arena diregenerate.

## Penerapan fisik oleh ArenaGenerator

Profil hasil normalisasi diterapkan sebelum struktur mode dibangun:

- **Fill:** setiap kolom rendah dinaikkan dengan DIRT dan permukaan baru ditutup GRASS_BLOCK.
- **Cut:** blok permukaan yang terlalu tinggi diubah menjadi AIR sampai elevasi target, lalu permukaan target ditutup GRASS_BLOCK.
- **Spawn pads:** area 9x9 RED/BLUE memiliki elevasi yang sama; jalur dan bangunan TDM maupun Relic Rush mengacu ke heightmap baru.
- **Mode offsets:** TDM memakai anchor ±27; Relic Rush baru memakai anchor ±32. `spawn-offset` disimpan per arena agar layout lama tidak memindahkan spawn sebelum regenerate.
- **Snapshot:** cut/fill dan semua struktur berada dalam satu daftar BlockEdit. SnapshotStore menyimpan BlockData asli untuk seluruh posisi itu sebelum perubahan world dilakukan.

Normalizer membatalkan rencana bila rentang cut/fill melintasi TileState. Snapshot saat ini tidak menyimpan inventory atau data block entity, sehingga chest, spawner, sign dengan data, dan block entity lain tidak boleh tertimpa. Batas world height juga diperiksa sebelum edit ditambahkan.

## Batas keselamatan tambahan

- Jika perubahan elevasi maksimum pada satu kolom melebihi `terrain.max-column-adjustment`, pembuatan arena ditolak.
- TerrainDetector memeriksa profil hasil normalisasi. Rentang elevasi di atas 48 blok ditolak; lonjakan antarkolom, jalur utama, dan keseimbangan spawn juga diverifikasi.
- Pengecekan ruang spawn dan objektif memahami volume yang akan dibersihkan oleh operasi cut, tetapi tetap menolak block entity atau obstruksi yang berada di luar volume cut.
- Tidak ada blok world yang disentuh sampai rencana selesai, lolos seluruh pemeriksaan, dan snapshot berhasil disimpan.

## Pemulihan

`/arena regenerate` dan `/arena mode` memulihkan snapshot asli sebelum memindai serta menormalisasi ulang terrain. `delete` dan `/arena recover` tetap menggunakan snapshot yang sama. Jangan menghapus folder `plugins/ArenaSystem/snapshots` bila ingin mempertahankan kemampuan restore/regenerate.

## Tes algoritme

Tes mandiri mencakup:
- Terrain datar: tidak ada kolom yang berubah.
- Tebing 12 blok dan 20 blok: beda tinggi tetangga maksimum 2 blok dan spawn seimbang.
- Peak tunggal 20 blok: kontur hasil memenuhi beda tinggi maksimum 2 blok.
- 250 heightmap acak pada masing-masing offset spawn ±27 dan ±32: setiap pasangan kolom tetangga memenuhi batas 2 blok dan pad spawn 9x9 sama rata.

Tes tersebut memvalidasi algoritme heightmap, bukan keseluruhan plugin atau operasi world Paper.
