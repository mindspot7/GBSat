package app.gbsat.android

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import app.gbsat.android.ui.GBSatRoot
import kotlin.math.abs

class MainActivity : ComponentActivity() {
    private var padMask = 0
    private val capHeld = HashSet<Int>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { GBSatRoot(onRequestAllFiles = ::requestAllFiles, onImmersive = ::setImmersiveMode, onQuitApp = ::quitApp) }
    }

    override fun onPause() {
        super.onPause()
        if (Emulator.running) Emulator.pause(true)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) setImmersiveMode(true) // hide the system bars again after dialogs, pickers or app switches
    }

    private fun quitApp() {
        NativeBridge.nativeShutdown()
        finishAndRemoveTask()
        android.os.Process.killProcess(android.os.Process.myPid()) // emulator state lives in the process; start clean next time
    }

    private fun requestAllFiles() {
        if (Build.VERSION.SDK_INT >= 30 && !Storage.hasAllFilesAccess()) {
            startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:$packageName")))
        } else if (Build.VERSION.SDK_INT < 30) {
            requestPermissions(arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE), 1)
        }
    }

    private fun setImmersiveMode(on: Boolean) {
        val c = WindowInsetsControllerCompat(window, window.decorView)
        if (on) {
            c.hide(WindowInsetsCompat.Type.systemBars())
            c.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } else c.show(WindowInsetsCompat.Type.systemBars())
    }

    // ---- Physical gamepad support ----
    private fun keyBit(code: Int): Int = Emulator.keyMap[code] ?: 0
    private var l2Key = false
    private var r2Key = false

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        // Remap dialog is open: the next physical button press becomes the binding.
        Emulator.remapTarget?.let { target ->
            val fromPad = event.source and (InputDevice.SOURCE_GAMEPAD or InputDevice.SOURCE_DPAD or InputDevice.SOURCE_KEYBOARD) != 0
            if (fromPad && event.keyCode != KeyEvent.KEYCODE_BACK) {
                if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                    Emulator.bindKey(target, event.keyCode); Emulator.remapTarget = null
                }
                return true
            }
        }
        // Hotkey capture: remember every key held until all are released; that set becomes the binding.
        Emulator.hotkeyCapture?.let { action ->
            if (event.source and (InputDevice.SOURCE_GAMEPAD or InputDevice.SOURCE_DPAD or InputDevice.SOURCE_KEYBOARD) != 0 &&
                event.keyCode != KeyEvent.KEYCODE_BACK
            ) {
                if (event.action == KeyEvent.ACTION_DOWN) {
                    capHeld += event.keyCode
                    Emulator.hotkeyCaptureKeys = Emulator.hotkeyCaptureKeys + event.keyCode
                } else if (event.action == KeyEvent.ACTION_UP) {
                    capHeld -= event.keyCode
                    if (capHeld.isEmpty() && Emulator.hotkeyCaptureKeys.isNotEmpty()) {
                        Emulator.bindHotkey(action, Emulator.hotkeyCaptureKeys)
                        Emulator.hotkeyCapture = null; Emulator.hotkeyCaptureKeys = emptySet()
                    }
                }
                return true
            }
        }
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

    private fun uiKey(event: KeyEvent): Boolean {
        val mapped = when (event.keyCode) {
            KeyEvent.KEYCODE_BUTTON_A -> KeyEvent.KEYCODE_DPAD_CENTER
            KeyEvent.KEYCODE_BUTTON_B -> KeyEvent.KEYCODE_BACK
            else -> event.keyCode
        }
        return super.dispatchKeyEvent(KeyEvent(event.downTime, event.eventTime, event.action, mapped, event.repeatCount,
            event.metaState, event.deviceId, event.scanCode, event.flags, event.source))
    }

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        if (event.source and InputDevice.SOURCE_JOYSTICK != 0 && event.action == MotionEvent.ACTION_MOVE && Emulator.running) {
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
