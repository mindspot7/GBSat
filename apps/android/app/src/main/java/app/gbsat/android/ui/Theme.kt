package app.gbsat.android.ui

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Dark purple "Saturn" palette used across the app
private val Violet = Color(0xFFB9A6FF)
private val Gold = Color(0xFFE8C26A)
private val scheme = darkColorScheme(
    primary = Violet, onPrimary = Color(0xFF1A1433), primaryContainer = Color(0xFF3A2D73), onPrimaryContainer = Color(0xFFEDE7FF),
    secondary = Gold, onSecondary = Color(0xFF2A1F05), secondaryContainer = Color(0xFF4A3A1A), onSecondaryContainer = Color(0xFFFFE9B8),
    tertiary = Color(0xFF7FD7C4),
    background = Color(0xFF1D1636), onBackground = Color(0xFFEDE9FF),
    surface = Color(0xFF221B3F), onSurface = Color(0xFFEDE9FF),
    surfaceVariant = Color(0xFF2E2655), onSurfaceVariant = Color(0xFFCBC3EA),
    surfaceContainer = Color(0xFF261F47), surfaceContainerHigh = Color(0xFF2E2655), surfaceContainerHighest = Color(0xFF372E63),
    surfaceContainerLow = Color(0xFF211A3D), surfaceContainerLowest = Color(0xFF1A1431),
    outline = Color(0xFF6E6596), outlineVariant = Color(0xFF433A6E),
)

/**
 * Small screens (e.g. 720x720 handhelds, ~360dp per side) get the whole interface scaled down, so the layouts made for
 * 480dp+ screens fit without cutting content. Screens with at least 480dp on the short side are not changed.
 */
@Composable
fun GBSatTheme(content: @Composable () -> Unit) {
    val cfg = androidx.compose.ui.platform.LocalConfiguration.current
    val d = androidx.compose.ui.platform.LocalDensity.current
    val shortSide = minOf(cfg.screenWidthDp, cfg.screenHeightDp).coerceAtLeast(1)
    val f = (shortSide / 480f).coerceIn(0.72f, 1f)
    androidx.compose.runtime.CompositionLocalProvider(
        androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(d.density * f, d.fontScale),
    ) { MaterialTheme(colorScheme = scheme, content = content) }
}
