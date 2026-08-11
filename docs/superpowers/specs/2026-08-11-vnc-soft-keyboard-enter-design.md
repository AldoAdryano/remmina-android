# VNC Soft Keyboard Enter Design

## Goal
Make the Android software keyboard Enter key reliably send X11 Return to the remote VNC desktop without changing SSH, SFTP, pointer, audio, or display-quality behavior.

## Root cause
RemoteX currently supports native Android `KEYCODE_ENTER`, but the hidden Compose `BasicTextField` used to summon the software keyboard only forwards inserted characters. Android IMEs are free to represent Enter as an inserted newline (`\n` or `\r`) or as an editor action such as Done, Go, Send, Search, or Next. Newline was therefore either not delivered as a native key event or was forwarded as Unicode LF (10), while RFB applications expect X11 Return (`0xFF0D`).

## Design
1. Define one reusable `KeySymMapper.RETURN = 0xFF0D` constant and use it for native `KEYCODE_ENTER`.
2. In the hidden VNC text input, translate inserted `\n` and `\r` to `RETURN`; ordinary characters keep their current behavior. Newline markers are not retained in the hidden composition buffer.
3. Handle common Android IME actions (Done, Go, Next, Search, Send) by dispatching the same Return keysym.
4. Add an explicit `Enter` toolbar button as a deterministic fallback and for shortcut combinations with armed modifiers.
5. Add a source regression check to debug and release CI.

## Compatibility
The change is isolated to VNC keyboard input. Existing modifiers are applied by the existing `dispatchKey` path, so `Ctrl + Enter`, `Alt + Enter`, and similar combinations continue to work through the toolbar arming mechanism.
