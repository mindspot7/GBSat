package app.gbsat.android

import app.gbsat.android.ui.SaturnControls
import gb.frame.ControlsCapture
import gb.frame.HomeKeys
import gb.frame.HotkeyAction
import gb.frame.Hotkeys
import gb.frame.HomeTheme
import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import gb.frame.setControllerContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import app.gbsat.android.ui.GBSatRoot
import kotlin.math.abs

class MainActivity : ComponentActivity() {
    private var padMask = 0
    // Pair a controller's synthetic KEYCODE_BACK with the same physical B press without suppressing
    // a real Back button or debouncing two intentional, rapid Back presses.
    private var lastGamepadBAt = 0L
    private var lastGamepadBDevice = -1
    private var pairedBackDevice = -1
    private var pairedBackDownTime = -1L
    // Remember whether gameplay was active before Android backgrounds the activity.
    // Emulator.paused is set to true below, so the pre-pause state must be captured first.
    private var resumeGameplayAfterForeground = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // The first D-pad press after a touch moves the focus right away (gb.frame.setControllerContent)
        setControllerContent { GBSatRoot(onImmersive = ::setImmersiveMode, onQuitApp = ::quitApp) }
    }

    override fun onPause() {
        // Capture intent before pausing. If the in-game menu was already open, do not
        // resume automatically when Android returns to the activity.
        resumeGameplayAfterForeground = Emulator.running && !Emulator.paused
        super.onPause()
        if (Emulator.running) Emulator.pause(true)
    }

    override fun onResume() {
        super.onResume()
        if (resumeGameplayAfterForeground) {
            resumeGameplayAfterForeground = false
            if (Emulator.running) Emulator.pause(false)
        }
    }

    // Leaving the app (Home, recents, another app) while in a game: write the auto save now, since Android may close
    // GBSat in the background and the in-game Exit (which also saves it) never runs
    override fun onStop() {
        super.onStop()
        if (Emulator.running) Emulator.autoSaveInBackground()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) setImmersiveMode(true) // hide the system bars again after dialogs, pickers or app switches
    }

    private fun quitApp() {
        Emulator.autoSaveBlocking()
        NativeBridge.nativeShutdown()
        finishAndRemoveTask()
        android.os.Process.killProcess(android.os.Process.myPid()) // emulator state lives in the process; start clean next time
    }

    private fun setImmersiveMode(on: Boolean) {
        val c = WindowInsetsControllerCompat(window, window.decorView)
        // The app intentionally stays immersive. Android Back is dispatched to the active Compose BackHandler.
        if (on) {
            c.hide(WindowInsetsCompat.Type.systemBars())
            c.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } else c.show(WindowInsetsCompat.Type.systemBars())
    }

    // ---- Physical gamepad support ----
    private fun keyBit(code: Int): Int = Emulator.keyMap[code] ?: 0
    private var l2Key = false
    private var r2Key = false

    @SuppressLint("RestrictedApi") // ComponentActivity's public override contract requires delegating unhandled keys to super.
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        // A controller can emit BUTTON_B and KEYCODE_BACK for the same press. In gameplay, B remains a Saturn button;
        // consume a paired synthetic Back rather than opening the emulator menu as a side effect.
        if (event.keyCode == KeyEvent.KEYCODE_BUTTON_B && event.action == KeyEvent.ACTION_DOWN) {
            lastGamepadBAt = event.eventTime
            lastGamepadBDevice = event.deviceId
        }
        val captureActive = ControlsCapture.active(SaturnControls)
        if (event.keyCode == KeyEvent.KEYCODE_BACK && !captureActive) {
            // Some pads report one B press twice: as BUTTON_B and as KEYCODE_BACK. Pair only the duplicate
            // from the same device and same press; never swallow every controller Back during gameplay.
            val fromController = event.source and (InputDevice.SOURCE_GAMEPAD or InputDevice.SOURCE_DPAD) != 0
            if (fromController && event.action == KeyEvent.ACTION_DOWN &&
                event.deviceId == lastGamepadBDevice &&
                event.eventTime >= lastGamepadBAt &&
                event.eventTime - lastGamepadBAt <= 150L
            ) {
                pairedBackDevice = event.deviceId
                pairedBackDownTime = event.downTime
                return true
            }
            if (event.action == KeyEvent.ACTION_UP &&
                event.deviceId == pairedBackDevice &&
                event.downTime == pairedBackDownTime
            ) {
                pairedBackDevice = -1
                pairedBackDownTime = -1L
                return true
            }
            return backKey(event)
        }
        if (event.keyCode == KeyEvent.KEYCODE_BUTTON_B && (!Emulator.running || Emulator.paused) && !captureActive) return backKey(event)
        // Library buttons are the kit's, the same in every app: X menu, Start theme, Select fullscreen, A on the quit dialog,
        // and putting the focus back when a key arrives while nothing has it. B is Back/Exit (LibraryBack, via backKey above).
        if (HomeKeys.dispatch(event, enabled = !Emulator.running && !ControlsCapture.active(SaturnControls))) return true
        // A remap/hotkey prompt is open: the kit's capture turns the next presses into bindings
        if (ControlsCapture.dispatch(event, SaturnControls)) return true
        // Some handhelds report face buttons with a keyboard source; accept any gamepad button keycode
        // In-game menu open (emulation paused): the controller navigates the menu
        if (Emulator.running && Emulator.paused && event.keyCode != KeyEvent.KEYCODE_BACK &&
            (KeyEvent.isGamepadButton(event.keyCode) || event.keyCode in dpadKeys) && !Hotkeys.isMenuKey(event.keyCode)
        ) {
            return uiKey(event)
        }
        val isPad = event.source and (InputDevice.SOURCE_GAMEPAD or InputDevice.SOURCE_DPAD) != 0 ||
            KeyEvent.isGamepadButton(event.keyCode) || Emulator.keyMap.containsKey(event.keyCode)
        if (isPad && Emulator.running && event.repeatCount == 0 &&
            (event.action == KeyEvent.ACTION_DOWN || event.action == KeyEvent.ACTION_UP)
        ) {
            val consumed = Hotkeys.onKey(event.keyCode, event.action == KeyEvent.ACTION_DOWN)
            Hotkeys.suppressedKeys().forEach { padMask = padMask and keyBit(it).inv() }
            Emulator.setPadMask(padMask)
            if (consumed) return true
        }
        if (isPad && Emulator.running && Hotkeys.isSuppressed(event.keyCode)) return true
        if (event.keyCode == KeyEvent.KEYCODE_BUTTON_L2) l2Key = event.action == KeyEvent.ACTION_DOWN
        if (event.keyCode == KeyEvent.KEYCODE_BUTTON_R2) r2Key = event.action == KeyEvent.ACTION_DOWN
        val bit = keyBit(event.keyCode)
        if (isPad && bit != 0 && Emulator.running) {
            when (event.action) {
                KeyEvent.ACTION_DOWN -> padMask = padMask or bit
                KeyEvent.ACTION_UP -> padMask = padMask and bit.inv()
            }
            Emulator.setPadMask(padMask)
            return true
        }
        // Outside games the menus are driven by the controller like a console: A selects, B goes back
        if (!Emulator.running) {
            if (event.keyCode == KeyEvent.KEYCODE_BUTTON_B) return backKey(event)
            val mapped = when (event.keyCode) {
                KeyEvent.KEYCODE_BUTTON_A -> KeyEvent.KEYCODE_DPAD_CENTER
                KeyEvent.KEYCODE_BUTTON_B -> KeyEvent.KEYCODE_BACK
                else -> null
            }
            if (mapped != null) {
                return super.dispatchKeyEvent(KeyEvent(event.downTime, event.eventTime, event.action, mapped, event.repeatCount,
                    event.metaState, event.deviceId, event.scanCode, event.flags, event.source))
            }
        }
        return super.dispatchKeyEvent(event)
    }

    private val dpadKeys = setOf(KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_LEFT,
        KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_CENTER)

    /**
     * Send one completed Back press straight to Compose's BackHandler. Duplicate controller keycodes are
     * filtered by their device/downTime pair above, not by a time debounce that would eat quick navigation.
     */
    private fun backKey(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_UP && !event.isCanceled) {
            onBackPressedDispatcher.onBackPressed()
        }
        return true
    }

    @SuppressLint("RestrictedApi") // Re-dispatch remapped gamepad keys through the Activity's normal event pipeline.
    private fun uiKey(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_BUTTON_B) return backKey(event)
        val mapped = when (event.keyCode) {
            KeyEvent.KEYCODE_BUTTON_A -> KeyEvent.KEYCODE_DPAD_CENTER
            KeyEvent.KEYCODE_BUTTON_B -> KeyEvent.KEYCODE_BACK
            else -> event.keyCode
        }
        return super.dispatchKeyEvent(KeyEvent(event.downTime, event.eventTime, event.action, mapped, event.repeatCount,
            event.metaState, event.deviceId, event.scanCode, event.flags, event.source))
    }

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        // While the in-game menu is open (paused) the D-pad/stick motion is left to Android, which turns it into D-pad
        // key events for the menu; consuming it here made the menu unusable on pads whose D-pad is a hat axis
        if (event.source and InputDevice.SOURCE_JOYSTICK != 0 && event.action == MotionEvent.ACTION_MOVE && Emulator.running && !Emulator.paused) {
            val x = event.getAxisValue(MotionEvent.AXIS_X)
            val y = event.getAxisValue(MotionEvent.AXIS_Y)
            val hx = event.getAxisValue(MotionEvent.AXIS_HAT_X)
            val hy = event.getAxisValue(MotionEvent.AXIS_HAT_Y)
            if (Emulator.analogPad) {
                val dead = 0.12f
                fun conv(v: Float) = if (abs(v) < dead) 0x80 else (((v + 1f) * 127.5f).toInt()).coerceIn(0, 255)
                Emulator.setPadStick(conv(x), conv(y))
            } else {
                var m = padMask and (SaturnButton.Up.bit or SaturnButton.Down.bit or SaturnButton.Left.bit or SaturnButton.Right.bit).inv()
                if (x < -0.5f || hx < -0.5f) m = m or SaturnButton.Left.bit
                if (x > 0.5f || hx > 0.5f) m = m or SaturnButton.Right.bit
                if (y < -0.5f || hy < -0.5f) m = m or SaturnButton.Up.bit
                if (y > 0.5f || hy > 0.5f) m = m or SaturnButton.Down.bit
                padMask = m; Emulator.setPadMask(m)
            }
            val ltv = maxOf(event.getAxisValue(MotionEvent.AXIS_LTRIGGER), event.getAxisValue(MotionEvent.AXIS_BRAKE))
            val rtv = maxOf(event.getAxisValue(MotionEvent.AXIS_RTRIGGER), event.getAxisValue(MotionEvent.AXIS_GAS))
            Emulator.setTriggers((ltv * 255).toInt(), (rtv * 255).toInt())
            // Pads whose L2/R2 only report axes: press the buttons bound to L2/R2
            val l2 = keyBit(KeyEvent.KEYCODE_BUTTON_L2)
            val r2 = keyBit(KeyEvent.KEYCODE_BUTTON_R2)
            if (l2 != 0 || r2 != 0) {
                var m = padMask
                if (l2 != 0) m = if (ltv > 0.5f) m or l2 else if (ltv < 0.3f && !l2Key) m and l2.inv() else m
                if (r2 != 0) m = if (rtv > 0.5f) m or r2 else if (rtv < 0.3f && !r2Key) m and r2.inv() else m
                if (m != padMask) { padMask = m; Emulator.setPadMask(m) }
            }
            return true
        }
        return super.dispatchGenericMotionEvent(event)
    }
}
