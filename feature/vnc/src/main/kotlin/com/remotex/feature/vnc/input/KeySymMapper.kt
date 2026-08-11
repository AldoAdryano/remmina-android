package com.remotex.feature.vnc.input

import android.view.KeyEvent

object KeySymMapper {
    fun fromAndroid(event: KeyEvent): Int? {
        val unicode = event.unicodeChar
        if (unicode in 0x20..0x7e) return unicode
        return when (event.keyCode) {
            KeyEvent.KEYCODE_ENTER -> RETURN
            KeyEvent.KEYCODE_DEL -> 0xff08
            KeyEvent.KEYCODE_TAB -> 0xff09
            KeyEvent.KEYCODE_ESCAPE -> 0xff1b
            KeyEvent.KEYCODE_DPAD_LEFT -> 0xff51
            KeyEvent.KEYCODE_DPAD_UP -> 0xff52
            KeyEvent.KEYCODE_DPAD_RIGHT -> 0xff53
            KeyEvent.KEYCODE_DPAD_DOWN -> 0xff54
            KeyEvent.KEYCODE_MOVE_HOME -> 0xff50
            KeyEvent.KEYCODE_MOVE_END -> 0xff57
            KeyEvent.KEYCODE_PAGE_UP -> 0xff55
            KeyEvent.KEYCODE_PAGE_DOWN -> 0xff56
            KeyEvent.KEYCODE_INSERT -> 0xff63
            KeyEvent.KEYCODE_FORWARD_DEL -> 0xffff
            KeyEvent.KEYCODE_F1 -> 0xffbe
            KeyEvent.KEYCODE_F2 -> 0xffbf
            KeyEvent.KEYCODE_F3 -> 0xffc0
            KeyEvent.KEYCODE_F4 -> 0xffc1
            KeyEvent.KEYCODE_F5 -> 0xffc2
            KeyEvent.KEYCODE_F6 -> 0xffc3
            KeyEvent.KEYCODE_F7 -> 0xffc4
            KeyEvent.KEYCODE_F8 -> 0xffc5
            KeyEvent.KEYCODE_F9 -> 0xffc6
            KeyEvent.KEYCODE_F10 -> 0xffc7
            KeyEvent.KEYCODE_F11 -> 0xffc8
            KeyEvent.KEYCODE_F12 -> 0xffc9
            else -> null
        }
    }

    const val CTRL_L = 0xffe3
    const val ALT_L = 0xffe9
    const val SHIFT_L = 0xffe1
    const val SUPER_L = 0xffeb
    const val RETURN = 0xff0d
    const val TAB = 0xff09
    const val ESC = 0xff1b
}
