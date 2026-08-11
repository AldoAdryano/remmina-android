# RemoteX Android

RemoteX is a mobile-first Android remote administration client with VNC, SSH, and SFTP.

## Android compatibility

RemoteX V1 uses **compileSdk 36**, **targetSdk 36**, and **minSdk 26**. The project pins AndroidX versions that remain compatible with the released Android 16 / API 36 SDK used by the cloud build.

## Remote desktop and Watch Mode

Normal desktop control uses the built-in RFB/VNC client. **Mode Menonton** keeps VNC connected for control, pauses VNC framebuffer traffic, and opens a separate timestamped H.264 + AAC MPEG-TS stream through the existing SSH connection so video playback can prioritize smoothness and A/V synchronization.

The Linux host needs `ffmpeg`, `xdpyinfo`, and `pactl` for Watch Mode. On Ubuntu/Debian they are provided by:

```bash
sudo apt install ffmpeg x11-utils pulseaudio-utils
```

Watch Mode opens no additional inbound port; its media stream travels through SSH.
