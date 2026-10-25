package app.gbsat.android.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.ui.graphics.Color
import app.gbsat.android.Emulator
import app.gbsat.android.SaturnButton
import gb.frame.ControlButton
import gb.frame.ControlsHost
import gb.frame.HotkeyAction
import gb.frame.PadButton
import gb.frame.PadLayout
import gb.frame.padRect

/**
 * What GBSat gives the kit's standard Controls page: the Saturn control pad (drawing and buttons) and where its
 * bindings and options live. The page itself, the prompts and the key capture are the kit's (ControlsPage,
 * ControlsCapturePrompts, ControlsCapture).
 */
internal object SaturnControls : ControlsHost {
    override val console = "Saturn"
    override val pad = SaturnPad

    override val buttons: List<ControlButton> = listOf(
        SaturnButton.Up, SaturnButton.Down, SaturnButton.Left, SaturnButton.Right, SaturnButton.A, SaturnButton.B,
        SaturnButton.C, SaturnButton.X, SaturnButton.Y, SaturnButton.Z, SaturnButton.L, SaturnButton.R, SaturnButton.Start,
    ).map { b ->
        ControlButton(
            id = b.name, label = label(b), color = faceColor(b),
            badge = when (b) { SaturnButton.Start -> "ST"; else -> b.name },
            icon = when (b) {
                SaturnButton.Up -> Icons.Default.KeyboardArrowUp
                SaturnButton.Down -> Icons.Default.KeyboardArrowDown
                SaturnButton.Left -> Icons.AutoMirrored.Filled.KeyboardArrowLeft
                SaturnButton.Right -> Icons.AutoMirrored.Filled.KeyboardArrowRight
                else -> null
            },
        )
    }

    override fun keysFor(id: String): List<Int> = Emulator.keysFor(SaturnButton.valueOf(id))
    override fun bindKey(id: String, keyCode: Int) = Emulator.bindKey(SaturnButton.valueOf(id), keyCode)
    override var remapTarget: String?
        get() = Emulator.remapTarget?.name
        set(value) { Emulator.remapTarget = value?.let { SaturnButton.valueOf(it) } }
    override fun resetKeyMap() = Emulator.resetKeyMap()

    override val hotkeys: Map<HotkeyAction, Set<Int>> get() = Emulator.hotkeys
    override fun bindHotkey(action: HotkeyAction, keys: Set<Int>) = Emulator.bindHotkey(action, keys)
    override var hotkeyCapture: HotkeyAction?
        get() = Emulator.hotkeyCapture
        set(value) { Emulator.hotkeyCapture = value }
    override var hotkeyCaptureKeys: Set<Int>
        get() = Emulator.hotkeyCaptureKeys
        set(value) { Emulator.hotkeyCaptureKeys = value }

    override var fastForwardToggle: Boolean
        get() = Emulator.ffToggle
        set(value) { Emulator.ffToggle = value; Emulator.savePrefs(); Emulator.applyOptions() }
    override var showTouchControls: Boolean
        get() = Emulator.showTouchControls
        set(value) { Emulator.showTouchControls = value; Emulator.savePrefs(); Emulator.applyOptions() }

    private fun label(b: SaturnButton) = when (b) {
        SaturnButton.Up -> "D-pad up"; SaturnButton.Down -> "D-pad down"; SaturnButton.Left -> "D-pad left"; SaturnButton.Right -> "D-pad right"
        SaturnButton.L -> "L shoulder"; SaturnButton.R -> "R shoulder"
        else -> "${b.name} button"
    }
}

private fun faceColor(b: SaturnButton) = when (b) {
    SaturnButton.A, SaturnButton.B, SaturnButton.C -> Color(0xFF5E5A78)
    SaturnButton.X, SaturnButton.Y, SaturnButton.Z -> Color(0xFF47435F)
    SaturnButton.L, SaturnButton.R -> Color(0xFF3A3652)
    SaturnButton.Start -> Color(0xFF6E58D8)
    else -> Color(0xFF2E2A45)
}

/** Saturn control pad for the shared controller diagram: D-pad, Start, L/R and the A B C / X Y Z face buttons. */
private val SaturnPad = PadLayout(listOf(
    PadButton("L", "L", x = .23f, y = .14f, w = .20f, h = .13f, wOfWidth = true, shape = padRect(10), color = faceColor(SaturnButton.L)),
    PadButton("R", "R", x = .77f, y = .14f, w = .20f, h = .13f, wOfWidth = true, shape = padRect(10), color = faceColor(SaturnButton.R)),
    PadButton("Up", icon = Icons.Default.KeyboardArrowUp, x = .23f, y = .33f, w = .17f, h = .17f, shape = padRect(), color = faceColor(SaturnButton.Up)),
    PadButton("Down", icon = Icons.Default.KeyboardArrowDown, x = .23f, y = .67f, w = .17f, h = .17f, shape = padRect(), color = faceColor(SaturnButton.Down)),
    PadButton("Left", icon = Icons.AutoMirrored.Filled.KeyboardArrowLeft, x = .15f, y = .50f, w = .17f, h = .17f, shape = padRect(), color = faceColor(SaturnButton.Left)),
    PadButton("Right", icon = Icons.AutoMirrored.Filled.KeyboardArrowRight, x = .31f, y = .50f, w = .17f, h = .17f, shape = padRect(), color = faceColor(SaturnButton.Right)),
    PadButton("Start", "START", x = .50f, y = .62f, w = .11f, h = .10f, wOfWidth = true, shape = RoundedCornerShape(50), color = faceColor(SaturnButton.Start)),
    PadButton("X", "X", x = .66f, y = .38f, w = .18f, h = .18f, color = faceColor(SaturnButton.X)),
    PadButton("Y", "Y", x = .75f, y = .33f, w = .18f, h = .18f, color = faceColor(SaturnButton.Y)),
    PadButton("Z", "Z", x = .84f, y = .38f, w = .18f, h = .18f, color = faceColor(SaturnButton.Z)),
    PadButton("A", "A", x = .64f, y = .62f, w = .18f, h = .18f, color = faceColor(SaturnButton.A)),
    PadButton("B", "B", x = .74f, y = .58f, w = .18f, h = .18f, color = faceColor(SaturnButton.B)),
    PadButton("C", "C", x = .84f, y = .62f, w = .18f, h = .18f, color = faceColor(SaturnButton.C)),
))
