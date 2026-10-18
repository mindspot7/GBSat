package app.ymir.android.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Violet = Color(0xFF8B7CFF)
private val dark = darkColorScheme(
    primary = Violet, onPrimary = Color(0xFF14111F), secondary = Color(0xFF5BD6C8),
    background = Color(0xFF0F0D17), surface = Color(0xFF17142A), surfaceVariant = Color(0xFF221E3A),
    onBackground = Color(0xFFEAE7FF), onSurface = Color(0xFFEAE7FF),
)
private val light = lightColorScheme(
    primary = Color(0xFF5B4BE0), secondary = Color(0xFF0E8F83),
    background = Color(0xFFF7F5FF), surface = Color.White, surfaceVariant = Color(0xFFEBE7FF),
)

@Composable
fun GBSatTheme(content: @Composable () -> Unit) =
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) dark else light, content = content)
