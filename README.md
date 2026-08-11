# RemoteX Android

RemoteX adalah client remote administration Android yang mobile-first untuk Linux: **Desktop VNC, Terminal SSH, File SFTP, remote audio, dan Mode Menonton** dalam satu aplikasi.

## Status

Current public candidate: **v1.0.0-rc1**.

## Fitur

- Desktop remote dengan built-in RFB/VNC client.
- SSH terminal berbasis Apache MINA SSHD.
- SFTP file browser/transfer.
- Trackpad default, klik kanan dua jari, two-finger scroll, direct touch.
- Fullscreen, rotate, clipboard, screenshot, modifier Ctrl/Alt/Shift/Super, Tab/Enter/Esc.
- Kualitas VNC: Otomatis, Performa, Seimbang, Tinggi.
- Remote system audio melalui SSH.
- **Mode Menonton**: H.264 + AAC MPEG-TS melalui SSH untuk video/audio yang lebih halus dan sinkron.
- Kredensial tersimpan dienkripsi dengan Android Keystore-backed AES-GCM.
- Export/import profil tanpa password.
- Signed release workflow untuk update APK berikutnya.

## Android compatibility

- `compileSdk = 36`
- `targetSdk = 36`
- `minSdk = 26` (Android 8.0+)

## Persiapan Linux

SSH/SFTP:

```bash
sudo apt update
sudo apt install -y openssh-server
sudo systemctl enable --now ssh
```

Fitur lengkap Desktop/Audio/Mode Menonton pada Ubuntu/Debian X11:

```bash
sudo apt install -y x11vnc pulseaudio-utils ffmpeg x11-utils
```

Mode Menonton membuka **tidak ada port inbound baru**; media dikirim melalui SSH.

## Instalasi

Download APK signed dari halaman **GitHub Releases** dan cocokkan SHA-256 dengan file `RemoteX-Android.sha256` yang disertakan pada release.

Build debug dan release memiliki application ID berbeda. Jika berpindah dari debug ke release, ekspor profil dari **Pengaturan → Ekspor profil (tanpa password)** lalu impor di build release. Password/passphrase harus dimasukkan ulang.

## Panduan

- [Panduan penggunaan lengkap](docs/PANDUAN_PENGGUNAAN.md)
- [Release checklist](docs/RELEASE_CHECKLIST.md)
- [Stable APK updates](docs/stable-apk-updates.md)
- [Security](SECURITY.md)
- [Privacy](PRIVACY.md)
- [Changelog](CHANGELOG.md)

## Security

VNC Authentication klasik lemah dan trafik VNC tidak terenkripsi. Gunakan Desktop VNC hanya pada LAN tepercaya atau VPN tepercaya. Jangan expose port VNC langsung ke Internet.

Jangan membuat issue publik yang berisi password, private key, signing keystore, token, atau server credential.

## License

MIT. Lihat `LICENSE` dan `THIRD_PARTY_LICENSES.md`.
