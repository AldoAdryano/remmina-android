# RemoteX VNC Polishing — Design

## Goal

Make the RemoteX VNC session feel like a phone-first remote desktop while preserving the VNC engine and the working connection flow proven in FIX 9.

## Recommended UX

- Enter VNC in landscape and fullscreen by default.
- The remote framebuffer owns the whole screen; controls are overlays, never permanent layout rows.
- Show the main control strip when the session opens, then auto-hide it after 3.5 seconds.
- Keep one small top-right control handle visible so controls can always be reopened without stealing normal remote clicks.
- Use compact icon-first controls for Back, Keyboard, Input Mode, Scaling, Clipboard, Screenshot, and Disconnect, while keeping Ctrl/Alt/Shift/Super/Tab/Esc available in the expanded strip.
- Keep `FIT_SCREEN` as the safe default because it shows the complete desktop without clipping.
- Add `FILL_SCREEN` ("Isi Layar") as an optional center-crop mode for users who prefer no letterboxing.
- Keep `ORIGINAL_SIZE` and `STRETCH` for compatibility.

## Trackpad and Gestures

- Trackpad remains the default input mode.
- Replace the fixed 1.25x cursor multiplier with bounded acceleration: precise for short movements, faster for long swipes.
- Reuse `TrackpadGestureInterpreter` from the Android view instead of duplicating pointer movement math.
- Use Android touch slop for tap-vs-move classification so two-finger right-click tolerates normal finger jitter.
- Two-finger movement scrolls with a slightly smaller threshold than FIX 9.
- One-finger tap = left click.
- Two-finger tap = right click.
- Long press + one-finger movement = drag.
- Three-finger tap = show keyboard.
- Double tap = double left click.

## Status and Errors

- Connection/reconnection/error states remain centered over the framebuffer.
- Clipboard/screenshot confirmations become transient bottom-center messages and clear automatically.
- The existing reconnect policy and VNC protocol behavior are unchanged.

## Architecture

- `VncScreen.kt`: owns fullscreen state, floating control visibility, auto-hide timing, toolbar composition, and transient UI messages.
- `VncSurfaceView.kt`: owns framebuffer drawing and Android gesture interpretation.
- `TrackpadGestureInterpreter.kt`: remains pure Kotlin and becomes the single source of cursor acceleration math.
- `VncTypes.kt`: gains `FILL_SCREEN` only; no protocol model changes.
- `VncViewModel.kt`: keeps `FIT_SCREEN` and `TRACKPAD` defaults; no connection flow changes.

## Non-goals

- No RFB protocol/encoding/authentication changes.
- No SSH or SFTP changes.
- No saved-profile/database migration.
- No remote-resolution negotiation.
- No advanced viewport panning while zoomed; this can be a later V1.x improvement.

## Acceptance Criteria

1. VNC opens fullscreen in landscape by default.
2. The framebuffer uses the full Compose screen and the toolbar overlays it.
3. The expanded controls auto-hide after 3.5 seconds and can always be reopened from a small handle.
4. `FIT_SCREEN`, `FILL_SCREEN`, `ORIGINAL_SIZE`, and `STRETCH` all render without changing RFB state.
5. Trackpad small movements remain precise while long swipes move farther.
6. Two-finger tap is less sensitive to tiny movement and still sends RFB right-click mask 4.
7. Existing VNC unit behavior and protocol code remain intact.
8. Static regression checks pass; GitHub Actions remains the authoritative full Android build verification.
