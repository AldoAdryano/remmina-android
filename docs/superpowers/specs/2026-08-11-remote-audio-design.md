# RemoteX FIX 12 — Remote Audio Design

## Goal
Add one-way remote system audio playback to the VNC screen without modifying the RFB protocol or destabilizing VNC, SSH, or SFTP.

## Recommended Architecture
Remote audio runs as an independent side-channel over the profile's SSH connection. The Android client authenticates through the existing SSH engine, opens a non-interactive exec channel, runs a fixed PulseAudio/PipeWire-compatible capture command using `pactl` + `parec`, receives signed 16-bit little-endian PCM at 48 kHz stereo, and writes it to Android `AudioTrack` in streaming mode.

VNC remains responsible only for framebuffer/input/clipboard. Audio failure is non-fatal to the VNC session.

## UX
- Add `Suara` to the floating VNC toolbar.
- Audio starts only when the user taps `Suara`.
- While streaming, button label becomes `Bisukan`.
- Android media volume controls playback volume.
- Audio stops when the VNC screen closes or when `Bisukan` is tapped.
- Saved SSH credentials are reused automatically.
- For `ALWAYS_ASK`, tapping `Suara` triggers the existing SSH authentication prompt.
- If SSH is disabled for the profile, show `Aktifkan SSH pada profil untuk audio`.
- If remote PulseAudio tools are unavailable, surface a concise message suggesting `sudo apt install pulseaudio-utils`.
- Audio errors never disconnect VNC.

## Remote Capture Command
Use a fixed shell command with no user-supplied interpolation:

```sh
sh -c 'command -v pactl >/dev/null 2>&1 && command -v parec >/dev/null 2>&1 || { echo REMOTEX_AUDIO_MISSING >&2; exit 127; }; sink=$(pactl info | sed -n "s/^Default Sink: //p" | head -n 1); [ -n "$sink" ] || { echo REMOTEX_AUDIO_NO_SINK >&2; exit 2; }; exec parec --device="${sink}.monitor" --format=s16le --rate=48000 --channels=2'
```

This targets classic PulseAudio and PipeWire installations exposing PulseAudio compatibility utilities.

## Components
### SSH exec transport
Extend `SshSessionHandle` with `openExec(command)` and introduce `ExecChannel` exposing stdout/stderr flows and close. Apache MINA implementation uses an exec channel with no PTY.

### Audio module
New `feature:audio` module:
- `RemoteAudioState`: Idle, Connecting, Playing, Failed.
- `RemoteAudioEngine`: start/stop contract.
- `SshPcmAudioEngine`: owns a dedicated SSH engine/session so Terminal/SFTP sessions are unaffected.
- `AndroidPcmPlayer`: wraps `AudioTrack`, handles 48 kHz stereo PCM stream, audio focus, and cleanup.
- `RemoteAudioViewModel`: coordinates state and lifecycle.

### App routing
`VncRoute` owns a `RemoteAudioViewModel`, supplies saved SSH auth when available, and presents the existing SSH auth prompt only when audio is explicitly requested and credentials are not saved.

### VNC screen
`VncScreen` receives audio state and callbacks. It renders `Suara`/`Bisukan` and short status overlays. No audio implementation lives in the VNC module.

## Error Handling
- SSH host-key verification state for audio is surfaced as a non-destructive status. Existing trusted host records are reused.
- Missing `pactl`/`parec` maps to an actionable installation message.
- No default sink maps to `Output audio Linux tidak ditemukan`.
- Broken stream or SSH disconnect maps to `Audio terputus` while VNC continues.
- All secrets are wiped after connection establishment using existing SSH engine semantics.

## Security
- Audio command is constant and never contains host/user-provided shell fragments.
- Traffic is encrypted by SSH.
- Credentials remain in the existing Android Keystore-backed store.
- No microphone capture is requested in FIX 12; therefore no Android microphone permission is needed.

## Testing
- Pure tests for audio command classification and state behavior.
- SSH regression/source test confirms exec-channel API and no-PTY implementation.
- Source test confirms VNC UI audio controls and lifecycle cleanup.
- GitHub Actions remains the authoritative full Android compile/lint/test/build verification.

## Out of Scope
- Android microphone forwarding to Linux.
- Audio compression/Opus.
- Audio over raw VNC/RFB.
- Windows/macOS capture backends.
