# Changelog

All notable public RemoteX Android changes are documented here.

## v1.0.0-rc1 — 2026-08-11

First public release candidate.

### Remote access
- Desktop remote control using the built-in RFB/VNC client.
- SSH terminal using Apache MINA SSHD.
- SFTP file browser and transfer workflow.
- One connection profile can provide Desktop, Terminal, and File access.

### Desktop experience
- Trackpad and direct-touch input modes.
- Two-finger right click and two-finger scrolling.
- Fullscreen, portrait/landscape rotation, multiple scale modes, clipboard, screenshot, and special keys.
- Android soft-keyboard Enter support.
- Display quality modes: Otomatis, Performa, Seimbang, and Tinggi.
- Tight/Hextile/RAW fallback VNC pipeline with framebuffer performance optimizations.
- High-quality mode memory hardening with safe fallback.

### Audio and video
- Remote Linux system audio streamed over SSH.
- Mode Menonton for timestamped H.264 + AAC playback over SSH using FFmpeg on the Linux host.
- Watch Mode pauses normal VNC framebuffer traffic while media playback is active and resumes VNC afterward.

### Security and reliability
- Saved credentials encrypted with Android Keystore-backed AES-GCM storage.
- Optional application lock.
- Persistent APK signing for future in-place release updates.
- Profile export/import intentionally excludes credentials.
- Known-host handling for SSH and redacted diagnostic logging.

### Release-candidate note
Mode Menonton and the latest High-quality VNC hardening are new in this release candidate. Report reproducible crashes with Android/logcat details, but do not include passwords, private keys, or other credentials.
