# RemoteX VNC Input Stability — Design

## Goal

Fix the three gesture regressions observed on a real phone after FIX 10: reversed two-finger scrolling, cursor jumps when a scroll gesture ends, and slow cursor drift toward the top-left during one-finger trackpad movement. Also make orientation testing explicit with an in-app rotate control.

## Root Causes

1. `TrackpadGestureInterpreter.move()` converted the absolute floating-point coordinate with `toInt()`. At positive screen coordinates this is asymmetric around zero: a tiny negative delta can decrement the integer coordinate while the same positive delta is truncated away. Repeated touch jitter therefore biases the cursor toward smaller X/Y values (top-left).
2. `VncSurfaceView` did not handle `ACTION_POINTER_UP`. After a two-finger gesture, `lastX/lastY` still represented the two-finger centroid. The following one-finger `ACTION_MOVE` could be interpreted as a large cursor delta even though the user was only finishing the scroll gesture.
3. Two-finger deltas were sent with the same sign as finger movement. The expected phone/touchpad behavior is natural scrolling, so the wheel direction must be inverted.
4. FIX 10 forced sensor-landscape for the VNC screen, so portrait testing was not discoverable from the app.

## Design

### Pointer integration

`TrackpadGestureInterpreter` keeps fractional X/Y remainders and converts movement deltas with symmetric `roundToInt()` before adding them to the integer remote cursor coordinate. Fractional remainder is preserved across events within one gesture and reset at gesture boundaries. Remainder is cleared on framebuffer clamping.

### Scroll integration

Two-finger scroll accumulation moves into `TrackpadGestureInterpreter.scroll()`. Finger deltas are inverted before conversion to RFB wheel steps: finger-up produces wheel-down, finger-down produces wheel-up. Fractional scroll distance remains accumulated until it reaches the 40 px step threshold.

### Multi-touch transition guard

A small pure-Kotlin `TrackpadTouchGuard` records whether the current touch sequence has ever contained multiple pointers. Once multi-touch begins, one-finger pointer movement stays suppressed until all fingers are lifted and a fresh `ACTION_DOWN` starts a new gesture. `ACTION_POINTER_UP` explicitly re-baselines the remaining touch position to prevent centroid discontinuity.

### Orientation

The VNC session still opens in sensor-landscape. The floating toolbar gains a `Putar` action that toggles between sensor-landscape and sensor-portrait. `MainActivity` handles `orientation|screenSize` configuration changes so rotating does not recreate the activity, trip the app-lock `onStop`, or dispose the active VNC composition. The requested orientation still resets to unspecified when leaving VNC.

### Test strategy

- JUnit tests lock in symmetric micro-jitter behavior, natural scroll direction, and multi-touch suppression.
- A standalone/static regression script checks the source contracts without requiring `kotlinc` on the user's laptop.
- The existing FIX 10 trackpad script gracefully falls back to static checks when `kotlinc`/Java are unavailable.
- GitHub Actions runs VNC source checks and remains authoritative for `testDebugUnitTest`, lint, and APK compilation.

## Non-goals

- No RFB authentication, encoding, framebuffer, or reconnect changes.
- No SSH/SFTP changes.
- No remote cursor-position pseudo-encoding.
- No configurable scroll inversion setting in this patch; natural scrolling becomes the corrected default.

## Acceptance Criteria

1. Repeated equal positive/negative sub-pixel jitter produces no net cursor drift.
2. Finger-up two-finger scroll sends wheel-down; finger-down sends wheel-up.
3. Lifting one finger after a two-finger gesture cannot move the remote pointer until the next fresh gesture.
4. Right-click two-finger tap remains available.
5. Toolbar includes `Putar`, toggling landscape/portrait without relying on Android auto-rotate.
6. Rotation does not recreate `MainActivity`, lock the app, or tear down the VNC session.
7. Existing VNC polish checks continue to pass.
8. GitHub Actions performs full Android unit/lint/build verification.
