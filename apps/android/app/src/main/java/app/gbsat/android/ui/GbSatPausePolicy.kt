package app.gbsat.android.ui

/**
 * Pure policy for pause-menu entry transitions.
 *
 * Native pause/resume remains owned by Emulator.pause; this object only decides
 * when the UI should request a transition and is safe to exercise in JVM tests.
 */
internal object GbSatPausePolicy {
    fun shouldHandleBack(menuVisible: Boolean): Boolean = !menuVisible

    fun shouldPauseForMenuRequest(menuVisible: Boolean): Boolean = !menuVisible

    fun shouldResumeOnEntry(paused: Boolean, menuVisible: Boolean): Boolean =
        paused && !menuVisible
}
