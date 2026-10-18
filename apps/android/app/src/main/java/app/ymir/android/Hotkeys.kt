package app.ymir.android

import android.view.KeyEvent

enum class HotkeyAction(val label: String) {
    QuickSave("Quick save"), QuickLoad("Quick load"), FastForward("Fast-forward"), Menu("Open menu"),
}

/** Human-readable name of a keycode, with L3/R3 spelled the way players expect. */
fun keyName(code: Int): String = when (code) {
    KeyEvent.KEYCODE_BUTTON_THUMBL -> "L3"
    KeyEvent.KEYCODE_BUTTON_THUMBR -> "R3"
    KeyEvent.KEYCODE_BUTTON_L1 -> "L1"
    KeyEvent.KEYCODE_BUTTON_R1 -> "R1"
    KeyEvent.KEYCODE_BUTTON_L2 -> "L2"
    KeyEvent.KEYCODE_BUTTON_R2 -> "R2"
    KeyEvent.KEYCODE_BUTTON_SELECT -> "Select"
    KeyEvent.KEYCODE_BUTTON_MODE -> "Mode"
    else -> KeyEvent.keyCodeToString(code).removePrefix("KEYCODE_").removePrefix("BUTTON_")
}

/**
 * Hotkey detection over physical gamepad/keyboard keys. A binding is a set of keys: one key = "button" hotkey,
 * several keys = "combination" hotkey (all must be held). Keys that take part in a triggered combination are
 * hidden from the game until released, so a combo like L1+R1+Start does not also press those Saturn buttons.
 */
object Hotkeys {
    private val pressed = HashSet<Int>()
    private val active = HashSet<HotkeyAction>()
    private val suppressed = HashSet<Int>()

    fun isSuppressed(code: Int) = code in suppressed
    fun suppressedKeys(): Set<Int> = suppressed.toSet()

    /** Returns true if the key was consumed by a hotkey (single-button binding). */
    fun onKey(code: Int, down: Boolean): Boolean {
        if (down) pressed += code else { pressed -= code; suppressed -= code }
        var consumed = false
        for (action in HotkeyAction.entries) {
            val binding = Emulator.hotkeys[action].orEmpty()
            if (binding.isEmpty()) continue
            val satisfied = pressed.containsAll(binding)
            if (satisfied && action !in active) {
                active += action
                if (binding.size > 1) suppressed += binding
                Emulator.runHotkey(action, true)
            } else if (!satisfied && action in active) {
                active -= action
                Emulator.runHotkey(action, false)
            }
            if (binding.size == 1 && code in binding) consumed = true
        }
        return consumed
    }

    fun reset() { pressed.clear(); active.clear(); suppressed.clear() }
}
