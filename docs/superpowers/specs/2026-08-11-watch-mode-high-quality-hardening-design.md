# RemoteX Watch Mode + High Quality Hardening Design

## Goal
Add a dedicated **Mode Menonton** that prioritizes smooth 720p/30 FPS playback, high visual quality, and A/V synchronization with an intentional 1.8–2.5 second buffer, while keeping VNC as the control plane. Also harden the existing VNC High mode so switching quality cannot terminate the Android app under heavy Tight/JPEG traffic.

## Problem evidence and root-cause hypothesis
FIX16 improves RFB video by using Tight/JPEG, but high-motion content can still produce large dirty rectangles. The current Tight JPEG path allocates a JPEG byte array, decoded Bitmap, pixel IntArray, and an emitted dirty-region IntArray for large rectangles. At high frame rates this creates heavy allocation/GC pressure. The user reports that switching to High exits the app immediately; without logcat we cannot prove an OOM, so FIX17 treats allocation pressure as the primary code-level risk and adds graceful decoder failure containment/telemetry instead of assuming one exception type.

RFB remains a remote framebuffer protocol and is not a timestamped A/V media transport. Therefore independent VNC video plus PCM-over-SSH can drift under load. Watch Mode must multiplex video and audio into one timestamped media stream.

## Architecture

### 1. VNC control plane remains connected
The existing VNC session stays alive in the background so the user can exit Watch Mode and immediately resume pointer/keyboard control. Existing quality modes remain available for desktop work.

### 2. Watch Mode media plane
A new `feature:watch` module opens an independent SSH session using the profile's existing SSH credentials. It runs FFmpeg remotely to:
- capture the active X11 display with `x11grab`;
- capture the default PulseAudio sink monitor;
- scale video to max 1280 px width while preserving aspect ratio;
- encode H.264/yuv420p at 30 FPS;
- encode AAC stereo at 48 kHz;
- multiplex both tracks into MPEG-TS written to stdout.

MPEG-TS is streamed through the encrypted SSH exec channel. AndroidX Media3 ExoPlayer 1.10.1 consumes the stream through a custom bounded DataSource and uses Android hardware media decoders where available. A 1.8-second playback threshold and 2.5–5 second working buffer are intentional to keep A/V synchronized and smooth.

### 3. Watch Mode UX
The VNC floating toolbar gets a `Menonton` button. Activating it:
- stops the separate PCM remote-audio engine if it is active (prevents duplicate sound);
- authenticates SSH exactly like the existing audio feature;
- overlays the Media3 video surface over the VNC framebuffer;
- shows `Menyiapkan stream…`, then `Mode Menonton • 720p30`;
- leaves a small floating `Keluar Menonton` control.

Exiting Watch Mode releases ExoPlayer, closes FFmpeg/SSH, and reveals the still-connected VNC session.

### 4. Server capability and failure behavior
Watch Mode requires `ffmpeg`, X11 capture support, PulseAudio/PipeWire Pulse compatibility, and H.264/AAC encoders. Missing prerequisites produce a user-facing message and never disconnect VNC.

Default remote command uses `DISPLAY` when valid, then tries `:0` and `:1`. It discovers screen dimensions with `xdpyinfo` and the default sink with `pactl`.

### 5. High mode hardening
The Tight JPEG decoder reuses its compressed-byte buffer, decoded mutable Bitmap when dimensions permit, and ARGB scratch IntArray. High mode JPEG quality is capped at 8 instead of 9 because the visual difference is small while encoded size/allocation spikes can be substantial. Decoder errors are converted into a recoverable VNC session failure/reconnect path; allocation failures in High mode request Balanced before reconnecting instead of allowing uncaught UI/process termination.

No automatic `System.gc()` and no broad process-level exception swallowing are introduced.

## Data flow

```text
Linux desktop ──VNC/RFB──────────────> RemoteX control/input
     │
     └─ FFmpeg x11grab + pulse
            │ H.264 + AAC, MPEG-TS
            └──── encrypted SSH stdout ──> bounded stream pipe
                                             │
                                             └─ Media3 ExoPlayer
                                                  │
                                                  ├─ hardware video decode
                                                  └─ synchronized audio output
```

## Defaults
- Watch Mode: 1280px max width, 30 FPS, H.264 CRF 20, AAC 160 kbps.
- Playback start buffer: 1800 ms.
- Minimum/maximum working buffer: 2500/5000 ms.
- VNC default quality remains Balanced.
- VNC High remains 32-bit Tight, JPEG quality 8.

## Security
- No new listening port is opened on the Linux host.
- Media travels inside SSH.
- Existing known-host and credential policies are reused.
- Remote FFmpeg command contains no credentials.

## Testing
- Pure tests for FFmpeg command construction and bounded stream pipe behavior.
- Source regression checks verify Media3 version, SSH-only transport, buffer targets, no microphone permission, and VNC High quality cap/reuse path.
- Existing FIX11–16 regression scripts remain green.
- Final Android compilation is verified by GitHub Actions (`testDebugUnitTest`, `lintDebug`, `assembleDebug`).
