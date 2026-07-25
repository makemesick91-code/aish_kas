# 📱 Cara Membuat File APK — Aish Kas (POS)

Folder ini adalah **proyek Android lengkap** dengan struktur yang sama persis seperti Aish Financial Tracker: aplikasi HTML offline (`app/src/main/assets/index.html`) dibungkus WebView native, plus jembatan Bluetooth untuk **cetak struk ke printer termal ESC/POS (58mm & 80mm)**.

Ada **3 cara** membuat APK-nya. **Cara A paling direkomendasikan** karena tidak perlu install software apa pun.

---

## ✅ CARA A — Build Otomatis di GitHub (Gratis, Tanpa Install Apa Pun)

Proyek ini sudah dilengkapi robot build (GitHub Actions). Kamu tinggal upload, robotnya yang membuat APK.

**Langkah:**

1. Buat akun di **github.com** (gratis) bila belum punya.
2. Klik **New repository** → beri nama (mis. `aish-kas`) → **Create repository**.
3. Di halaman repo baru, klik **"uploading an existing file"** → **drag semua isi folder `aish-kas-android` ini** ke sana (termasuk folder `app`, `gradle`, `.github`, dan file `gradlew`, `settings.gradle`, dll). Lalu **Commit changes**.
   - Penting: yang di-upload adalah **isi** folder `aish-kas-android`, bukan folder-nya. File `settings.gradle` harus berada di root repo.
4. Buka tab **Actions** di repo → akan muncul workflow **"Build APK"** yang berjalan otomatis (atau klik **Run workflow**).
5. Tunggu ± 3–5 menit sampai muncul centang hijau ✓.
6. Klik run tersebut → scroll ke bagian **Artifacts** → unduh **`AishKas-debug-apk`**.
7. Ekstrak file zip-nya → di dalamnya ada **`app-debug.apk`**. Itu APK kamu! 🎉

---

## ✅ CARA B — Layanan Web-to-APK

Karena aplikasi ini satu file HTML mandiri, layanan seperti **Median.co** atau **WebToApp.design** juga bisa dipakai (host `index.html` dulu, mis. lewat app.netlify.com/drop).

> ⚠ Catatan khusus Aish Kas: fitur **cetak struk Bluetooth** butuh jembatan native (`MainActivity.java`), jadi APK hasil layanan web-to-APK **tidak bisa mencetak**. Untuk POS, gunakan Cara A atau C.

---

## ✅ CARA C — Android Studio (Di Komputer Sendiri)

1. Install **Android Studio** (gratis, dari developer.android.com).
2. **File → Open** → pilih folder `aish-kas-android` ini.
3. Tunggu proses **Gradle sync** selesai.
4. Menu **Build → Build Bundle(s) / APK(s) → Build APK(s)**.
5. APK ada di: `app/build/outputs/apk/debug/app-debug.apk`

Atau lewat terminal: `./gradlew assembleDebug`

---

## 🔐 (Opsional) APK Rilis Bertanda Tangan

Sama seperti proyek Financial Tracker: buat keystore dengan `keytool`, tambahkan blok `signingConfigs` di `app/build.gradle`, lalu `./gradlew assembleRelease`.

---

## 🖨 Cara Pakai Printer Bluetooth

1. Nyalakan printer termal (58mm/80mm), pasangkan lewat **Pengaturan Bluetooth HP** (PIN umumnya `0000` atau `1234`).
2. Buka Aish Kas → **Setelan → Printer Bluetooth** → pilih ukuran kertas → **Cari Printer** → ketuk printermu.
3. Tekan **Tes Cetak**. Selesai — struk otomatis tercetak setiap transaksi, dan bisa dicetak ulang dari **Riwayat**.

---

## ℹ️ Info Teknis Aplikasi

- **Package / App ID:** `id.aish.kas`
- **Nama aplikasi:** Aish Kas
- **Minimal Android:** 7.0 (API 24) — **Target:** Android 14 (API 34)
- **Izin:** hanya Bluetooth (untuk printer struk). Tidak ada akses internet — 100% offline & privat.
- **Penyimpanan data:** localStorage di dalam WebView (produk, transaksi, pengaturan — permanen di HP).
- **Cadangan:** Setelan → Cadangkan (file JSON ke folder Download) → bisa dipulihkan kapan saja.
- **Isi aplikasi:** `app/src/main/assets/index.html` (untuk update tampilan/fitur, ganti file ini lalu build ulang).
- **Jembatan native:** `MainActivity.java` — `window.AishApp` (simpan file + printer ESC/POS via RFCOMM/SPP).

Butuh bantuan build? Bilang saja, nanti dipandu langkah demi langkah. 💙
