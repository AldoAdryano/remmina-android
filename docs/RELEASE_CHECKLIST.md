# RemoteX Android Release Checklist

Checklist ini digunakan untuk rilis GitHub APK signed.

## 1. Source
- Working tree bersih.
- Branch `main` sudah berisi FIX terbaru.
- Debug GitHub Actions hijau.
- `git diff --check` tidak menghasilkan error.

## 2. Signing
GitHub Actions repository secrets berikut harus tersedia:

- `REMOTEX_KEYSTORE_BASE64`
- `REMOTEX_KEYSTORE_PASSWORD`
- `REMOTEX_KEY_ALIAS`
- `REMOTEX_KEY_PASSWORD`

Jangan commit `.jks`, `.keystore`, password, atau secret ke repository.

## 3. Release candidate
Tag awal:

```bash
git tag -a v1.0.0-rc1 -m "RemoteX Android v1.0.0-rc1"
git push origin v1.0.0-rc1
```

Push tag memicu workflow **Release APK**.

Workflow harus melewati:

- secret scan;
- regression/source checks;
- unit tests;
- Android lint;
- signed `assembleRelease`;
- `apksigner verify`;
- SHA-256 generation;
- GitHub Release publication.

## 4. Release assets
GitHub Release harus berisi:

- `RemoteX-Android-v1.0.0-rc1.apk`
- `RemoteX-Android.sha256`

Verifikasi checksum setelah download:

```bash
sha256sum -c RemoteX-Android.sha256
```

## 5. Device smoke test
Pada APK release, uji:

- membuat/mengimpor profil;
- Desktop VNC connect/disconnect/reconnect;
- pointer, scroll, klik kanan, keyboard Enter;
- quality modes;
- rotate dan fullscreen;
- Terminal SSH;
- File SFTP;
- Suara remote;
- Mode Menonton;
- export/import profil;
- application lock bila digunakan.

## 6. Promosi ke v1.0.0
Jika RC lolos smoke test tanpa blocker, buat tag final dari commit yang sama atau dari commit perbaikan terakhir:

```bash
git tag -a v1.0.0 -m "RemoteX Android v1.0.0"
git push origin v1.0.0
```
