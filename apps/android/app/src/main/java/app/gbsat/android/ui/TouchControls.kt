package app.gbsat.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.gbsat.android.Emulator
import app.gbsat.android.SaturnButton
import kotlin.math.abs
import kotlin.math.hypot

/** Aggregates every on-screen control into one mask so multi-touch never clobbers itself. */
private object TouchState {
    private val held = HashMap<Any, Int>()
    fun set(owner: Any, bits: Int) {
        synchronized(this) {
            if (bits == 0) held.remove(owner) else held[owner] = bits
            Emulator.setTouchMask(held.values.fold(0) { a, b -> a or b })
        }
    }
}

private val padColor = Color.White.copy(alpha = .22f)
private val padPressed = Color.White.copy(alpha = .5f)

@Composable
fun TouchControls(modifier: Modifier, landscape: Boolean) {
    BoxWithConstraints(modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        // Scale the controls down on small or square screens so they never overlap each other
        val needW = 400.dp
        val needH = if (landscape) 330.dp else 290.dp
        val s = minOf(1f, maxWidth / needW, maxHeight / needH).coerceAtLeast(0.5f)
        fun Modifier.scaled(originX: Float, originY: Float) =
            graphicsLayer { scaleX = s; scaleY = s; transformOrigin = TransformOrigin(originX, originY) }
        val shoulderTop = if (landscape) 56.dp * s else 0.dp

        // Shoulders
        Row(Modifier.align(Alignment.TopStart).padding(top = shoulderTop).scaled(0f, 0f)) { Shoulder("L", SaturnButton.L) }
        Row(Modifier.align(Alignment.TopEnd).padding(top = shoulderTop).scaled(1f, 0f)) { Shoulder("R", SaturnButton.R) }

        // Left: D-pad or analog stick
        Box(Modifier.align(if (landscape) Alignment.BottomStart else Alignment.CenterStart).padding(bottom = 16.dp * s)
            .scaled(0f, if (landscape) 1f else 0.5f)) {
            if (Emulator.analogPad) AnalogStick() else DPad()
        }

        // Right: face buttons
        Box(Modifier.align(if (landscape) Alignment.BottomEnd else Alignment.CenterEnd).padding(bottom = 16.dp * s)
            .scaled(1f, if (landscape) 1f else 0.5f).size(190.dp)) {
            Face("X", SaturnButton.X, Modifier.align(Alignment.TopStart).offset(x = 0.dp, y = 8.dp))
            Face("Y", SaturnButton.Y, Modifier.align(Alignment.TopCenter).offset(y = (-4).dp))
            Face("Z", SaturnButton.Z, Modifier.align(Alignment.TopEnd).offset(y = 8.dp))
            Face("A", SaturnButton.A, Modifier.align(Alignment.BottomStart).offset(y = (-8).dp))
            Face("B", SaturnButton.B, Modifier.align(Alignment.BottomCenter).offset(y = 4.dp))
            Face("C", SaturnButton.C, Modifier.align(Alignment.BottomEnd).offset(y = (-8).dp))
        }

        // Start
        Box(Modifier.align(Alignment.BottomCenter).scaled(0.5f, 1f)) { StartButton() }
    }
}

@Composable
private fun PressableBox(owner: Any, bits: Int, modifier: Modifier, shape: androidx.compose.ui.graphics.Shape, content: @Composable BoxScope.() -> Unit) {
    var down by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current
    Box(
        modifier
            .background(if (down) padPressed else padColor, shape)
            .pointerInput(bits) {
                awaitEachGesture {
                    awaitFirstDown(); down = true; TouchState.set(owner, bits); haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    do { val e = awaitPointerEvent() } while (e.changes.any { it.pressed })
                    down = false; TouchState.set(owner, 0)
                }
            },
        contentAlignment = Alignment.Center, content = content,
    )
}

@Composable
private fun Face(label: String, b: SaturnButton, modifier: Modifier) =
    PressableBox(b, b.bit, modifier.size(58.dp), CircleShape) { Text(label, color = Color.White, fontSize = 20.sp) }

@Composable
private fun Shoulder(label: String, b: SaturnButton) =
    PressableBox(b, b.bit, Modifier.size(width = 96.dp, height = 44.dp), RoundedCornerShape(12.dp)) { Text(label, color = Color.White, fontSize = 18.sp) }

@Composable
private fun StartButton() =
    PressableBox("start", SaturnButton.Start.bit, Modifier.size(width = 84.dp, height = 36.dp), RoundedCornerShape(18.dp)) { Text("START", color = Color.White, fontSize = 13.sp) }

@Composable
private fun DPad() {
    var size by remember { mutableStateOf(IntSize.Zero) }
    var mask by remember { mutableIntStateOf(0) }
    Box(
        Modifier.size(170.dp).onSizeChanged { size = it }
            .background(padColor, CircleShape)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val first = awaitFirstDown()
                    fun update(p: Offset) {
                        val c = Offset(size.width / 2f, size.height / 2f)
                        val d = p - c
                        var m = 0
                        val dead = size.width * 0.12f
                        // Allow diagonals when both axes exceed the dead zone.
                        if (d.x > dead) m = m or SaturnButton.Right.bit
                        if (d.x < -dead) m = m or SaturnButton.Left.bit
                        if (d.y > dead) m = m or SaturnButton.Down.bit
                        if (d.y < -dead) m = m or SaturnButton.Up.bit
                        mask = m; TouchState.set("dpad", m)
                    }
                    update(first.position)
                    do {
                        val e = awaitPointerEvent()
                        e.changes.firstOrNull()?.let { update(it.position) }
                    } while (e.changes.any { it.pressed })
                    mask = 0; TouchState.set("dpad", 0)
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        fun lit(b: SaturnButton) = if (mask and b.bit != 0) padPressed else Color.Transparent
        Box(Modifier.align(Alignment.TopCenter).size(52.dp, 60.dp).background(lit(SaturnButton.Up), RoundedCornerShape(12.dp)))
        Box(Modifier.align(Alignment.BottomCenter).size(52.dp, 60.dp).background(lit(SaturnButton.Down), RoundedCornerShape(12.dp)))
        Box(Modifier.align(Alignment.CenterStart).size(60.dp, 52.dp).background(lit(SaturnButton.Left), RoundedCornerShape(12.dp)))
        Box(Modifier.align(Alignment.CenterEnd).size(60.dp, 52.dp).background(lit(SaturnButton.Right), RoundedCornerShape(12.dp)))
    }
}

@Composable
private fun AnalogStick() {
    var size by remember { mutableStateOf(IntSize.Zero) }
    var knob by remember { mutableStateOf(Offset.Zero) }
    Box(
        Modifier.size(170.dp).onSizeChanged { size = it }.background(padColor, CircleShape)
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown()
                    val radius = size.width / 2f
                    do {
                        val e = awaitPointerEvent()
                        val p = e.changes.firstOrNull() ?: break
                        var d = p.position - Offset(radius, radius)
                        val len = hypot(d.x, d.y)
                        if (len > radius) d = d * (radius / len)
                        knob = d
                        Emulator.setTouchStick(((d.x / radius + 1f) * 127.5f).toInt().coerceIn(0, 255), ((d.y / radius + 1f) * 127.5f).toInt().coerceIn(0, 255))
                    } while (e.changes.any { it.pressed })
                    knob = Offset.Zero; Emulator.setTouchStick(0x80, 0x80)
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.offset { androidx.compose.ui.unit.IntOffset(knob.x.toInt(), knob.y.toInt()) }.size(64.dp).background(padPressed, CircleShape))
    }
}
