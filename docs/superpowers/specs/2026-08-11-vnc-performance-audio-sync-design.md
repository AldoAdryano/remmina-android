# RemoteX FIX 13 — VNC Performance & Audio Sync Design

## Goal
Improve perceived desktop frame rate on Android and reduce audio/video mismatch while preserving the stable VNC, SSH, SFTP, input, rotation, and signing behavior from FIX 12.

## Root causes observed in FIX 12
1. The RFB client advertises only Raw and CopyRect for real pixel data, so changed desktop regions can arrive uncompressed.
2. The client requests 32-bit pixels, using four network bytes per pixel.
3. Every framebuffer update clones the entire framebuffer even when only a small region changed. This remains intentionally unchanged in FIX 13 because the current frame stream can conflate/drop intermediate updates; keeping complete snapshots prevents visual corruption.
4. `VncSurfaceView` recycles and recreates a full Android `Bitmap` for every received frame.
5. Remote audio allocates an approximately 200 ms `AudioTrack` target buffer and does not request Android's low-latency performance path.

## Selected approach
### Video transport
- Prefer standard RFB Hextile encoding (encoding 5), while retaining CopyRect and Raw fallback.
- Request RGB565 16-bit little-endian true-color pixels as the performance format. The Android framebuffer remains ARGB8888 after decode.
- Keep DesktopSize and LastRect pseudo-encodings.

### Frame delivery and rendering
- Keep full immutable frame snapshots because the current frame flow is conflated/drop-oldest; this preserves correctness when intermediate frames are skipped.
- `VncSurfaceView` owns one persistent ARGB8888 bitmap per remote resolution and updates only the dirty region from the latest full snapshot with `Bitmap.setPixels`.
- Drawing is scheduled with `postInvalidateOnAnimation()` to coalesce rendering around display vsync.
- Screenshot behavior stays unchanged because every delivered frame remains a complete immutable framebuffer snapshot.

### Audio latency
- Keep 48 kHz stereo PCM16 over the existing encrypted SSH exec stream.
- Request `AudioTrack.PERFORMANCE_MODE_LOW_LATENCY`.
- Reduce the effective target playback buffer from 200 ms to 60 ms, never below Android's minimum supported buffer.
- Reduce SSH exec read chunks from 16 KiB to 4 KiB so PCM can be delivered to `AudioTrack` in smaller increments.

## Compatibility and safety
- No new Android permissions.
- No change to VNC authentication, SSH authentication, host-key checks, SFTP, stable APK signing, or connection profiles.
- Hextile decoder follows RFC 6143 and always retains Raw fallback.
- If a VNC server does not choose Hextile it can continue using Raw/CopyRect.
- Audio failure remains isolated from the VNC session.

## Success criteria
- Static/GUI desktop interaction avoids per-frame Android Bitmap recreation and receives compressed/lower-bandwidth RFB data where supported.
- Server can select Hextile and RGB565 is decoded correctly.
- Existing Raw and CopyRect paths remain supported.
- Android bitmap is reused across same-size updates.
- Audio buffer target is 60 ms and low-latency mode is requested.
- Existing FIX 11 input-stability and FIX 12 audio source checks remain green.
