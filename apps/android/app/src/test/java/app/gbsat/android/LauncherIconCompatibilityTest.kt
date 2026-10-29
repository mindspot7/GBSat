package app.gbsat.android

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LauncherIconCompatibilityTest {
    @Test fun opaqueLauncherIconIsLimitedToRgDs() {
        assertTrue(LauncherIconCompatibility.shouldUseRgDsIcon("Anbernic", "RG DS"))
        assertTrue(LauncherIconCompatibility.shouldUseRgDsIcon(" anbernic ", " rg ds "))
        assertFalse(LauncherIconCompatibility.shouldUseRgDsIcon("Anbernic", "RG Rotate"))
        assertFalse(LauncherIconCompatibility.shouldUseRgDsIcon("Anbernic", "RG Cube"))
        assertFalse(LauncherIconCompatibility.shouldUseRgDsIcon("Retroid", "RG DS"))
    }
}
