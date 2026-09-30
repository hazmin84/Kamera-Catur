# Kamera Catur (Android Native App) ♟️📸

Aplikasi Android Native moden berprestasi tinggi untuk mengimbas papan catur fizikal atau digital menggunakan kamera telefon, mengekstrak koordinat dan kedudukan buah catur secara automatik ke format FEN (*Forsyth–Edwards Notation*), memberikan panduan langkah terbaik berasaskan enjin **Stockfish AI**, serta membolehkan pengguna menyambung permainan catur secara interaktif terus dalam aplikasi.

---

## 🌟 Ciri-Ciri Utama

1. **📸 Pengimbas Kamera Papan Catur (Camera Vision Engine)**
   - Rakam gambar papan catur fizikal (kayu, vinil) atau skrin (Chess.com / Lichess).
   - Mengesan grid $8 \times 8$ (a1 hingga h8), mensegmentasi 64 petak secara tepat.
   - Pengecaman warna petak (terang/gelap) dan buah catur (Raja, Permaisuri, Benteng, Gajah, Kuda, Bidak) bagi Putih & Hitam.
   - Auto-generate FEN string kedudukan semasa.

2. **🧠 Bantuan & Analisis Enjin Stockfish AI (Dual-Side Hints)**
   - Panduan langkah terbaik (*Best Move*) serentak untuk **kedua-dua pihak** (Putih & Hitam).
   - Penilaian kedudukan (*Centipawn score / Mate score*) dan cadangan variasi langkah taktikal.
   - Pilihan tahap kesukaran AI dan kedalaman analisis (*depth*).

3. **♟️ Papan Catur Interaktif (Playable Chess Engine)**
   - Sambung bermain catur secara langsung dari kedudukan yang dirakam.
   - Tema visual papan dan buah berinspirasikan standard **Chess.com** (Green/Cream & Classic Wood).
   - Pengesahan langkah sah (*Legal Move Validation*), rekod notasi PGN, cawangan semak/mat (*check/checkmate*), dan mod latihan (*Practice Mode*).

---

## 🛠️ Seni Bina & Struktur Projek

```
Kamera-Catur/
├── app/
│   ├── src/main/
│   │   ├── AndroidManifest.xml
│   │   ├── java/com/yeayyy/cameracatur/
│   │   │   ├── MainActivity.kt               # Entrypoint & ViewPager / BottomNav Navigation
│   │   │   ├── vision/
│   │   │   │   ├── ChessBoardDetector.kt     # Pemprosesan Imej & Pengecaman Grid 8x8
│   │   │   │   └── ChessImageScannerActivity.kt # Antaramuka Kamera & Pemilihan Foto
│   │   │   ├── chess/
│   │   │   │   ├── ChessBoard.kt             # Logik Papan Catur, FEN & Validasi Langkah
│   │   │   │   ├── ChessPiece.kt             # Model Buah Catur & Nilai
│   │   │   │   └── ChessMove.kt              # Notasi Langkah & Simpanan Rekod
│   │   │   ├── engine/
│   │   │   │   └── StockfishEngineService.kt # Komunikasi Enjin AI & Analisis Dwi-Pihak
│   │   │   └── ui/
│   │   │       ├── ChessBoardView.kt         # Custom View Papan Interaktif Gaya Chess.com
│   │   │       └── AnalysisDashboardActivity.kt # Papan Pemuka Analisis & Panduan Langkah
│   │   └── res/
│   │       ├── layout/                       # Susun atur UI Moden & Kemas
│   │       ├── drawable/                     # Aset Vektor Buah Catur & Ikon
│   │       └── values/                       # Tema Warna, Gaya & String
├── build.gradle
├── settings.gradle
└── README.md
```

---

## 🚀 Keperluan & Pemasangan

- **Platform:** Android 8.0 (API 26) ke atas (Disyorkan Android 12+)
- **Bahasa:** Kotlin & Java
- **SDK Build Tools:** 34.0.0+
- **Gradle:** 8.0+

---

## 📄 Lesen & Hak Cipta
Hak Cipta Terpelihara © 2026 **Yeayyy Solution** (Muhammad Hazmin).
Lesen terbuka di bawah terma [MIT License](LICENSE).
