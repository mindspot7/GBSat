package app.gbsat.emulator.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class GbSatPausePolicyTest {
    @Test
    fun backOnlyOpensMenuWhenItIsHidden() {
        assertEquals(true, GbSatPausePolicy.shouldHandleBack(menuVisible = false))
        assertEquals(false, GbSatPausePolicy.shouldHandleBack(menuVisible = true))
    }

    @Test
    fun menuRequestOnlyRequestsPauseWhenMenuWasHidden() {
        assertEquals(true, GbSatPausePolicy.shouldPauseForMenuRequest(menuVisible = false))
        assertEquals(false, GbSatPausePolicy.shouldPauseForMenuRequest(menuVisible = true))
    }

    @Test
    fun entryResumesOnlyWhenPausedAndMenuIsHidden() {
        assertEquals(true, GbSatPausePolicy.shouldResumeOnEntry(paused = true, menuVisible = false))
        assertEquals(false, GbSatPausePolicy.shouldResumeOnEntry(paused = true, menuVisible = true))
        assertEquals(false, GbSatPausePolicy.shouldResumeOnEntry(paused = false, menuVisible = false))
        assertEquals(false, GbSatPausePolicy.shouldResumeOnEntry(paused = false, menuVisible = true))
    }
}
