# NovaPlayer

Pemutar video Android native — Kotlin, Jetpack Compose (Material 3), Media3 ExoPlayer.

## Fitur
- **Perpustakaan video** dari penyimpanan: thumbnail, durasi, ukuran, filter folder, pencarian, urut (terbaru/nama/durasi/ukuran)
- **Buka file** (document picker) dan **stream URL** (MP4, HLS `.m3u8`, DASH `.mpd`)
- **Pemutar**: play/pause, lompat ±10 dtk, sebelumnya/berikutnya (playlist per daftar), seekbar
- **Gestur**: ketuk = tampil/sembunyi kontrol · ketuk dua kali kiri/kanan = ±10 dtk · geser vertikal kiri = kecerahan · geser vertikal kanan = volume · geser horizontal = seek
- **Kecepatan** 0.25x – 3x
- **Trek audio** dan **subtitle** (embedded + muat file `.srt` / `.vtt` / `.ass`)
- **Mode rasio**: Fit / Zoom / Stretch
- **Picture-in-Picture** (otomatis saat menekan Home saat memutar)
- **Kunci layar**, **putar layar**, layar tetap menyala
- **Lanjut menonton** — posisi terakhir tiap video diingat
- Bisa dipilih sebagai aplikasi pemutar default (intent `VIEW video/*`)

## Cara build
1. Buka folder `NovaPlayer` di **Android Studio** (Koala atau lebih baru) — Gradle wrapper akan dibuat otomatis, atau jalankan `gradle wrapper` sekali.
2. Tunggu sinkronisasi Gradle, lalu **Run** di perangkat/emulator (minSdk 24).

## Struktur
```
app/src/main/java/com/nova/player/
├── MainActivity.kt          # host layar perpustakaan
├── PlayerActivity.kt        # ExoPlayer, PiP, rotasi, subtitle, resume
├── data/VideoRepository.kt  # query MediaStore
├── ui/LibraryScreen.kt      # daftar video
├── ui/PlayerScreen.kt       # kontrol & gestur pemutar
├── ui/theme/Theme.kt        # tema gelap
└── util/Format.kt
```

## Catatan
Kode ini belum saya kompilasi di sini (tidak ada Android SDK di lingkungan ini), jadi jika Gradle menampilkan galat kecil, kirimkan pesannya dan saya perbaiki.

Ide lanjutan: pemutaran latar belakang (MediaSessionService), sleep timer, pengaturan, tema terang, cast.
