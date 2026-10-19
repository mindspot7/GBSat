package app.gbsat.android

import android.view.Surface

/** JNI surface of libgbsat.so (see cpp/gbsat_jni.cpp). */
object NativeBridge {
    init { System.loadLibrary("gbsat") }

    @JvmStatic external fun nativeInit(backupRamPath: String, smpcPath: String): String?
    @JvmStatic external fun nativeShutdown()
    @JvmStatic external fun nativeSetSurface(surface: Surface?)
    @JvmStatic external fun nativeLoadIpl(path: String): String?
    @JvmStatic external fun nativeLoadDisc(path: String, preload: Boolean): String?
    @JvmStatic external fun nativeEjectDisc()
    @JvmStatic external fun nativeSetTrayOpen(open: Boolean)
    @JvmStatic external fun nativeReset(hard: Boolean)
    @JvmStatic external fun nativeSetPaused(paused: Boolean)
    @JvmStatic external fun nativeSetFastForward(ff: Boolean)
    @JvmStatic external fun nativeSetButtons(mask: Int)
    @JvmStatic external fun nativeSetAnalog(x: Int, y: Int, l: Int, r: Int)
    @JvmStatic external fun nativeSetPadType(analog: Boolean)
    @JvmStatic external fun nativeSetSH2Cache(on: Boolean)
    @JvmStatic external fun nativeSetThreadedVdp(on: Boolean)
    @JvmStatic external fun nativeSetResolutionScale(scale: Int)
    @JvmStatic external fun nativeGetDiscHeader(): String?
    @JvmStatic external fun nativeGetLastFrame(): IntArray?
    @JvmStatic external fun nativeSetCdReadSpeed(factor: Int)
    @JvmStatic external fun nativeSetFrameSkip(on: Boolean)
    @JvmStatic external fun nativeSetCartridge(type: Int)
    @JvmStatic external fun nativeGetStats(): LongArray
    @JvmStatic external fun nativeSetRenderer(mode: Int)
    @JvmStatic external fun nativeGetGpuError(): String?
    @JvmStatic external fun nativeSetCrashFile(path: String)

    // RetroAchievements
    @JvmStatic external fun nativeRaInit(userAgent: String)
    @JvmStatic external fun nativeRaLogin(user: String, secret: String, isToken: Boolean)
    @JvmStatic external fun nativeRaLogout()
    @JvmStatic external fun nativeRaSetHardcore(on: Boolean)
    @JvmStatic external fun nativeRaPollEvents(): ByteArray
    @JvmStatic external fun nativeRaAchievements(): ByteArray
    @JvmStatic external fun nativeRaHttpResponse(id: Int, status: Int, body: ByteArray?)

    /** Called from native code (rcheevos) to perform an HTTP request; the response goes back via nativeRaHttpResponse. */
    @JvmStatic fun raHttp(id: Int, url: String, post: String?, contentType: String?, userAgent: String) =
        RetroAchievements.httpRequest(id, url, post, contentType, userAgent)
    @JvmStatic external fun nativeSetDisplayOptions(filter: Int, shader: Int)
    @JvmStatic external fun nativeSetCheats(addrs: LongArray, values: LongArray, sizes: IntArray)
    @JvmStatic external fun nativeSetFastBoot(on: Boolean)
    @JvmStatic external fun nativeGetDiscHash(): String?
    @JvmStatic external fun nativeSaveState(path: String): String?
    @JvmStatic external fun nativeLoadState(path: String): String?
}

/** Bit positions match the core peripheral::Button. */
enum class SaturnButton(val bit: Int) {
    Right(1 shl 15), Left(1 shl 14), Down(1 shl 13), Up(1 shl 12), Start(1 shl 11),
    A(1 shl 10), C(1 shl 9), B(1 shl 8), R(1 shl 7), X(1 shl 6), Y(1 shl 5), Z(1 shl 4), L(1 shl 3)
}
