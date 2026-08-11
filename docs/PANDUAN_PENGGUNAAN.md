# Panduan Penggunaan RemoteX Android

Panduan ini berlaku untuk **RemoteX Android v1.0.0-rc1**.

RemoteX menggabungkan tiga fungsi utama dalam satu profil koneksi:

- **Desktop (VNC)** untuk mengendalikan tampilan Linux.
- **Terminal (SSH)** untuk command line.
- **File (SFTP)** untuk membuka dan memindahkan file.

RemoteX juga menyediakan **Suara** untuk audio sistem Linux dan **Mode Menonton** untuk video + audio yang lebih halus dan sinkron.

## 1. Persyaratan

### Android
- Android 8.0 / API 26 atau lebih baru.
- HP dan komputer Linux berada pada jaringan yang saling dapat dijangkau, misalnya Wi-Fi/LAN yang sama atau VPN tepercaya.

### Linux untuk SSH/SFTP
Ubuntu/Debian:

```bash
sudo apt update
sudo apt install -y openssh-server
sudo systemctl enable --now ssh
ss -ltnp | grep ':22'
```

Cari IP komputer:

```bash
hostname -I
```

### Linux untuk Desktop VNC pada sesi X11
Contoh dengan `x11vnc`:

```bash
sudo apt install -y x11vnc
mkdir -p ~/.vnc
x11vnc -storepasswd ~/.vnc/x11vnc.pass
x11vnc \
  -display :0 \
  -auth guess \
  -forever \
  -shared \
  -rfbauth ~/.vnc/x11vnc.pass \
  -rfbport 5900
```

Jika display bukan `:0`, cek sesi Xorg:

```bash
ps aux | grep '[X]org'
```

Lalu sesuaikan `-display :0` menjadi display yang aktif.

> VNC klasik tidak mengenkripsi framebuffer/input. Gunakan hanya pada LAN tepercaya atau VPN tepercaya. Jangan membuka port VNC langsung ke Internet.

### Linux untuk Suara dan Mode Menonton

```bash
sudo apt install -y pulseaudio-utils ffmpeg x11-utils
command -v pactl
command -v parec
command -v ffmpeg
command -v xdpyinfo
```

Mode Menonton menggunakan SSH, sehingga tidak membutuhkan port inbound tambahan selain SSH.

## 2. Membuat profil koneksi

1. Buka **RemoteX**.
2. Tekan **Tambah koneksi**.
3. Isi **Nama koneksi**.
4. Isi **Host / IP**, misalnya `192.168.100.7`.
5. Isi **Username SSH/SFTP**.
6. Aktifkan **VNC** jika Desktop dibutuhkan dan isi port VNC, biasanya `5900`.
7. Aktifkan **SSH/SFTP** dan isi port SSH, biasanya `22`.
8. Pilih autentikasi SSH yang digunakan.
9. Pilih kebijakan kredensial:
   - **Selalu tanyakan**: password diminta ketika diperlukan.
   - **Simpan terenkripsi**: secret disimpan terenkripsi menggunakan Android Keystore.
10. Tekan **Simpan**.

Satu profil yang sama dapat digunakan untuk Desktop, Terminal, File, Suara, dan Mode Menonton selama layanan host tersedia.

## 3. Desktop (VNC)

Pada kartu profil tekan **Desktop**.

### Kontrol dasar
- **Trackpad** adalah mode input default.
- Geser satu jari: memindahkan pointer.
- Tap satu jari: klik kiri.
- Tap dua jari: klik kanan.
- Geser dua jari: scroll.
- Tekan `⋮` di kanan atas untuk membuka kembali toolbar jika toolbar tersembunyi.

### Tombol toolbar
- **Ctrl / Alt / Shift / Super**: modifier keyboard Linux/PC.
- **Tab / Enter / Esc**: tombol khusus.
- **Trackpad / Sentuh**: mengganti mode input.
- **Pas Layar / Isi Layar / Asli / Regang**: mengganti skala desktop.
- **Kualitas**: mengganti profil kualitas VNC.
- **Suara**: memutar suara sistem Linux di HP.
- **Menonton**: masuk Mode Menonton.
- **Fullscreen / Jendela**: tampilan layar penuh atau dengan system bar.
- **Putar**: mengganti potret/lanskap tanpa mengandalkan Auto Rotate Android.
- **Keyboard**: membuka keyboard Android.
- **Clipboard**: mengirim teks clipboard Android ke remote.
- **Screenshot**: menyimpan snapshot desktop remote.
- **Putuskan**: menutup sesi VNC.

### Kualitas VNC
- **Otomatis**: RemoteX menyesuaikan profil sesuai performa sesi.
- **Performa**: prioritas kelancaran dan bandwidth rendah.
- **Seimbang**: rekomendasi untuk penggunaan sehari-hari.
- **Tinggi**: prioritas detail gambar pada jaringan cepat.

Untuk YouTube/video, gunakan **Mode Menonton** daripada memaksa VNC mode Tinggi menjadi video streamer.

## 4. Terminal (SSH)

Pada kartu profil tekan **Terminal**.

RemoteX membuat sesi SSH ke host dan port yang tersimpan pada profil. Gunakan seperti terminal Linux biasa. Jika profil menggunakan **Selalu tanyakan**, masukkan password/passphrase ketika dialog autentikasi muncul.

## 5. File (SFTP)

Pada kartu profil tekan **File**.

Tampilan SFTP memisahkan area lokal dan remote. Gunakan fungsi yang tersedia untuk membuka folder dan melakukan transfer. Transfer SFTP menggunakan koneksi SSH yang sama dan berjalan melalui jalur background/IO, bukan thread UI.

## 6. Suara remote

1. Sambungkan **Desktop**.
2. Buka toolbar `⋮`.
3. Tekan **Suara**.
4. Jika diperlukan, masukkan autentikasi SSH.
5. Atur volume menggunakan volume media Android.
6. Tekan **Bisukan** untuk menghentikan audio remote.

Audio remote menggunakan capture output Linux melalui `pactl/parec` dan mengirimkannya melalui SSH. Jika dependency tidak tersedia, instal `pulseaudio-utils` pada Linux.

## 7. Mode Menonton

Gunakan Mode Menonton untuk video seperti YouTube ketika prioritasnya adalah **FPS, kualitas gambar, dan sinkronisasi audio-video**, bukan latensi kontrol paling rendah.

1. Sambungkan **Desktop**.
2. Buka video pada komputer Linux.
3. Mulai playback.
4. Buka toolbar RemoteX.
5. Tekan **Menonton**.
6. Tunggu sekitar beberapa detik agar buffer video/audio terbentuk.
7. RemoteX memutar stream H.264 + AAC yang dikirim lewat SSH.
8. Tekan **Keluar Menonton** untuk kembali ke framebuffer VNC normal.

Delay beberapa detik pada Mode Menonton adalah disengaja untuk membantu playback yang lebih stabil dan sinkron. Saat Mode Menonton aktif, RemoteX mem-pause traffic framebuffer VNC agar host tidak melakukan dua capture layar berat secara bersamaan.

## 8. Menggunakan tombol Super

**Super** setara dengan tombol Windows/Meta pada keyboard PC. Contoh Ubuntu:

- `Super` membuka Overview/Activities.
- `Super + A` membuka daftar aplikasi.
- `Super + L` mengunci sesi.
- `Super + Left/Right` dapat digunakan untuk snap window tergantung desktop environment.

Tekan modifier **Super** di toolbar, lalu tekan tombol/karakter berikutnya.

## 9. Ekspor profil dan backup

Buka **Pengaturan** lalu pilih **Ekspor profil (tanpa password)**.

File JSON menyimpan data profil seperti nama, host, username, port, dan pilihan autentikasi, tetapi **tidak menyimpan password, passphrase, atau private-key secret**.

Untuk mengembalikan profil:

1. Buka **Pengaturan**.
2. Tekan **Impor profil**.
3. Pilih file `remotex-profiles.json`.
4. Masukkan ulang kredensial yang diperlukan.

## 10. Migrasi dari APK debug ke APK release

Build debug dan release adalah dua package Android yang berbeda:

- Debug: `com.remotex.android.debug`
- Release: `com.remotex.android`

Karena itu rilis pertama dapat terpasang berdampingan dengan build debug.

Cara migrasi yang direkomendasikan:

1. Pada RemoteX Debug: **Pengaturan → Ekspor profil (tanpa password)**.
2. Instal APK Release.
3. Pada RemoteX Release: **Pengaturan → Impor profil**.
4. Masukkan ulang password/passphrase yang diperlukan.
5. Uji Desktop, Terminal, File, Suara, dan Mode Menonton.
6. Setelah yakin release bekerja, build debug boleh dihapus.

Release berikutnya dengan package dan signing key yang sama dapat dipasang sebagai update di atas release sebelumnya.

## 11. Troubleshooting singkat

### Desktop: `ECONNREFUSED`
Pastikan VNC server sedang listening:

```bash
ss -ltnp | grep ':5900'
```

### Terminal/File tidak terhubung

```bash
sudo systemctl status ssh
ss -ltnp | grep ':22'
```

### Suara tidak tersedia

```bash
command -v pactl
command -v parec
pactl info
```

### Mode Menonton tidak mulai

```bash
command -v ffmpeg
command -v xdpyinfo
command -v pactl
```

### Firewall Ubuntu
Contoh hanya untuk LAN `192.168.100.0/24`:

```bash
sudo ufw allow from 192.168.100.0/24 to any port 22 proto tcp
sudo ufw allow from 192.168.100.0/24 to any port 5900 proto tcp
```

## 12. Keamanan

- Jangan expose VNC langsung ke Internet.
- Gunakan LAN/VPN tepercaya.
- Jangan commit file `.jks`, private key, password, atau profile export yang sensitif ke repository public.
- Saat mengirim log bug, periksa kembali agar password/private key/token tidak ikut terkirim.
- Aktifkan **Kunci aplikasi** bila perangkat mendukung biometrik/kunci perangkat.
