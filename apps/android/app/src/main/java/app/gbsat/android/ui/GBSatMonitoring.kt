package app.gbsat.android.ui

import app.gbsat.android.Emulator
import gb.frame.MonitoringSettings
import gb.frame.PerfOptions

/** Saturn applies the shared monitoring controls to its actual emulator options. */
object GBSatMonitoring : MonitoringSettings {
    override val options get() = PerfOptions(Emulator.showFps, Emulator.showSpeed, Emulator.showCpu, Emulator.showGpu,
        Emulator.showRenderer, Emulator.showResolution, Emulator.showFrameTime)
    override val showFfButton get() = Emulator.showFfButton
    override fun update(options: PerfOptions, showFfButton: Boolean) {
        Emulator.showFps = options.fps; Emulator.showSpeed = options.speed
        Emulator.showCpu = options.cpu; Emulator.showGpu = options.gpu
        Emulator.showRenderer = options.renderer; Emulator.showResolution = options.resolution
        Emulator.showFrameTime = options.frameTime; Emulator.showFfButton = showFfButton
        Emulator.savePrefs()
    }
}
