package app.ymir.android

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
import app.ymir.android.ui.YmirRoot
import kotlin.math.abs

class MainActivity : ComponentActivity() {
    private var padMask = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { YmirRoot(onRequestAllFiles = ::requestAllFiles, onImmersive = ::setImmersiveMode) }
    }

    override fun onPause() {
        super.onPause()
        if (Emulator.running) Emulator.setPaused(true)
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
    private fun keyBit(code: Int): Int = when (code) {
        KeyEvent.KEYCODE_DPAD_UP -> SaturnButton.Up.bit
        KeyEvent.KEYCODE_DPAD_DOWN -> SaturnButton.Down.bit
        KeyEvent.KEYCODE_DPAD_LEFT -> SaturnButton.Left.bit
        KeyEvent.KEYCODE_DPAD_RIGHT -> SaturnButton.Right.bit
        KeyEvent.KEYCODE_BUTTON_A -> SaturnButton.B.bit
        KeyEvent.KEYCODE_BUTTON_B -> SaturnButton.A.bit
        KeyEvent.KEYCODE_BUTTON_X -> SaturnButton.Y.bit
        KeyEvent.KEYCODE_BUTTON_Y -> SaturnButton.X.bit
        KeyEvent.KEYCODE_BUTTON_L1 -> SaturnButton.L.bit
        KeyEvent.KEYCODE_BUTTON_R1 -> SaturnButton.R.bit
        KeyEvent.KEYCODE_BUTTON_L2 -> SaturnButton.Z.bit
        KeyEvent.KEYCODE_BUTTON_R2 -> SaturnButton.C.bit
        KeyEvent.KEYCODE_BUTTON_START -> SaturnButton.Start.bit
        else -> 0
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val isPad = event.source and (InputDevice.SOURCE_GAMEPAD or InputDevice.SOURCE_DPAD) != 0
        val bit = keyBit(event.keyCode)
        if (isPad && bit != 0 && Emulator.running) {
            when (event.action) {
                KeyEvent.ACTION_DOWN -> padMask = padMask or bit
                KeyEvent.ACTION_UP -> padMask = padMask and bit.inv()
            }
            Emulator.setPadMask(padMask)
            return true
        }
        return super.dispatchKeyEvent(event)
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
            val lt = (event.getAxisValue(MotionEvent.AXIS_LTRIGGER) * 255).toInt()
            val rt = (event.getAxisValue(MotionEvent.AXIS_RTRIGGER) * 255).toInt()
            Emulator.setTriggers(lt, rt)
            return true
        }
        return super.dispatchGenericMotionEvent(event)
    }
}
