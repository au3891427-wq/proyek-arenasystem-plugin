# TerrainDetector — ArenaSystem 0.2.3.3

Detector menganalisis heightmap hasil normalisasi. Ia tidak mengubah terrain. Evaluasi dipakai untuk TDM dan Relic Rush karena keduanya memakai titik spawn RED/BLUE dan jalur utama yang seimbang. Jalur TDM dievaluasi dari offset ±27; Relic Rush baru dari offset ±32. Arena lama mempertahankan offset tersimpan sampai regenerate.

## Metrik

- Rentang elevasi total.
- Lonjakan tinggi maksimum antar kolom bertetangga.
- Rata-rata beda tinggi antar kolom.
- Proporsi batas kolom dengan lonjakan minimal 5 blok.
- Selisih elevasi spawn RED/BLUE.
- Maksimum dan rata-rata beda tinggi di jalur tengah RED ke BLUE.

## Ambang penolakan

- Rentang elevasi lebih dari 48 blok.
- Selisih elevasi spawn lebih dari 8 blok.
- Lonjakan tetangga mencapai 9 blok atau lebih.
- Jalur tengah berubah lebih dari 5 blok per langkah.
- Lebih dari 10% tepi kolom memiliki lonjakan minimal 5 blok.

Ambang peringatan diberikan untuk rentang lebih dari 28, selisih spawn lebih dari 4, lonjakan tetangga minimal 5, jalur tengah minimal 3, dan proporsi tepi curam di atas 3.5%. TerrainDetector berjalan setelah TerrainNormalizer; oleh karena itu, batas `max-adjacent-step` biasanya sudah menghilangkan lonjakan lokal yang menjadi alasan penolakan.

Detector heightmap belum menganalisis kualitas line-of-sight, gua, overhang, material licin, struktur buatan di seluruh arena, atau kelayakan jalur alternatif secara penuh. Pengecekan block entity dan ruang spawn/objektif dilakukan terpisah oleh generator/plugin.
