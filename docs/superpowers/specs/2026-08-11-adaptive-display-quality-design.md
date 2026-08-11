# RemoteX FIX 14 — Adaptive Display Quality Design

## Goal
Restore full-color VNC quality without losing the performance gains from FIX 13, while allowing the user to switch display quality at runtime and see a lightweight FPS indicator.

## User Experience
- Default quality is **Seimbang**.
- Toolbar exposes a **Kualitas** menu with: Otomatis, Performa, Seimbang, Tinggi.
- A small unobtrusive overlay shows `<fps> FPS • <mode>` while connected.
- In Auto mode the overlay also shows the effective profile, e.g. `Auto · Performa`.
- Switching quality never disconnects VNC and never changes SSH/SFTP/audio behavior.

## Quality Profiles
- **Performa**: RGB565 16-bit; Hextile preferred. Lowest bandwidth.
- **Seimbang**: 32-bit/24-depth true color; Hextile preferred. Default and recommended.
- **Tinggi**: 32-bit/24-depth true color; RAW preferred, Hextile fallback. Intended for fast LAN where decode simplicity is preferred over bandwidth.
- **Otomatis**: starts in Seimbang. During sustained framebuffer activity, switches to Performa after two slow 1-second windows (<18 FPS) and returns to Seimbang after three healthy windows (>=24 FPS). Static/idle screens do not cause downgrades.

## Protocol Safety
RFB SetPixelFormat/SetEncodings changes are applied only at a framebuffer-update boundary. The engine records requested mode immediately but changes decoder/pixel format after the current server update has completed, then requests a non-incremental full refresh in the new format. This prevents old-format bytes from being decoded with the new format.

## Architecture
- `VncQualityMode` is public UI/domain state.
- `VncQualityProfile` maps a mode to pixel format and encoding preference.
- `AdaptiveQualityController` is pure Kotlin and owns hysteresis/state transitions for Auto.
- `RfbVncEngine` owns selected/effective quality and FPS measurements.
- `VncViewModel` exposes selected quality and performance stats to Compose.
- `VncScreen` owns only the quality menu and indicator presentation.

## Constraints
- Keep RFB 3.3/3.7/3.8 compatibility.
- Keep Hextile, RAW, CopyRect, DesktopSize, LastRect support.
- Do not alter VNC auth, trackpad FIX 11, audio FIX 12/13, SSH, SFTP, database, or signing.
- Auto mode never promotes above Balanced.
