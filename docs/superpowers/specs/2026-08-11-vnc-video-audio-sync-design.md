# RemoteX FIX 16 — VNC Video Throughput & Audio Sync Design

## Goal

Make dynamic desktop/video playback materially smoother than FIX 14 while keeping normal desktop interaction stable, and reduce the perceived lead/lag between remote video and SSH-streamed system audio. FIX 16 also includes the unapplied FIX 15 Android soft-keyboard Enter fix so it can be applied directly on top of FIX 14.

## Root Cause

FIX 14 advertises only Hextile/CopyRect/Raw. Hextile is effective for UI regions but is inefficient for rapidly changing high-colour video, which causes large framebuffer updates and decode/copy pressure. The client also waits until a decoded framebuffer has been copied/emitted before requesting the next update, adding avoidable request latency to RFB's client-pull cycle. Remote audio is raw PCM over a separate SSH channel and starts almost immediately, so it can get ahead of a slower video path.

## VNC Transport Design

RemoteX will add a clean Kotlin Tight decoder and advertise Tight before Hextile/CopyRect/Raw. Tight support includes solid fill, JPEG, copy, palette, and gradient filters, four persistent zlib streams, stream-reset bits, compact lengths, and the Tight 24-bit RGB special case. Decoder inputs are bounded before allocation/decompression because VNC server data is untrusted.

Quality modes remain user-facing but now tune Tight rather than using Raw as the high-quality strategy:

- Performance: RGB565, Tight quality 4, compression 1.
- Balanced: 32-bit full colour, Tight quality 7, compression 2.
- High: 32-bit full colour, Tight quality 9, compression 3.
- Auto: retains the existing Balanced/Performance hysteresis.

Hextile, CopyRect and Raw remain fallbacks for compatibility.

## Frame Request Pipeline

After a complete framebuffer update is decoded, RemoteX will calculate performance/quality changes and send the next FramebufferUpdateRequest before making the UI snapshot. Only one request remains outstanding at a time; this simply moves the request earlier so server encoding/network work can overlap local frame delivery. The UI snapshot contains only the dirty bounding rectangle rather than cloning the entire framebuffer on every update. `VncSurfaceView` applies that region to its persistent bitmap; screenshots are captured from that bitmap.

## Audio Synchronisation

Audio remains an independent SSH PCM stream. The player gains an initial prebuffer instead of calling `AudioTrack.play()` immediately. An `AudioSyncPolicy` selects the prebuffer from the most recent VNC FPS at the moment audio is started:

- FPS >= 24: 120 ms
- FPS 12–23: 180 ms
- FPS 1–11: 240 ms
- FPS unavailable/idle: 180 ms

The AudioTrack allocation includes enough room for the chosen delay plus safety headroom. Playback starts only after at least the target number of PCM bytes has been written. The active delay is exposed in the audio state/status message for diagnosis.

This is pragmatic sync, not timestamp-perfect A/V sync: RFB framebuffer updates do not carry media presentation timestamps.

## Keyboard

Carry forward FIX 15: Android soft keyboard newline, carriage return, and IME actions map to X11 Return; toolbar provides an explicit Enter key.

## Security & Compatibility

No GPL library code is linked or copied. RemoteX implements protocol behaviour in Kotlin. Tight decoder validates rectangle dimensions, compact lengths, decompressed sizes, palette indexes, JPEG dimensions and zlib output bounds. Existing VNC authentication, pointer/scroll fixes, rotation, SSH, SFTP, signing and saved credential behaviour remain unchanged.

## Verification

- Pure tests for quality/Tight preferences, compact-length decoding, palette/gradient helpers, and audio sync policy.
- Source regression checks for Tight advertisement, request-before-frame-copy ordering, bounded decoder logic, audio prebuffer, and FIX 15 Enter handling.
- Existing FIX 11–15 regression scripts remain green.
- GitHub Actions remains the authoritative full Android `testDebugUnitTest lintDebug assembleDebug` verification.
