package app.gbsat.android

import gb.frame.RaBackend

/** RetroAchievements for GBSat: the rcheevos client in the native library, game hashes from the disc images. */
object GBSatRa : RaBackend {
    override fun init(userAgent: String) = NativeBridge.nativeRaInit(userAgent)
    override fun login(user: String, secret: String, isToken: Boolean) = NativeBridge.nativeRaLogin(user, secret, isToken)
    override fun logout() = NativeBridge.nativeRaLogout()
    override fun setHardcore(on: Boolean) = NativeBridge.nativeRaSetHardcore(on)
    override fun pollEvents(): ByteArray = NativeBridge.nativeRaPollEvents()
    override fun achievements(): ByteArray = NativeBridge.nativeRaAchievements()
    override fun httpResponse(id: Int, status: Int, body: ByteArray?) = NativeBridge.nativeRaHttpResponse(id, status, body)
    override fun hashGame(path: String): String? =
        Storage.open(path)?.use { NativeBridge.nativeRaHashFile(it.path) }
            // A CHD/ISO this device can't read through its descriptor is hashed from a cached copy (as when launching it)
            ?: if (path.endsWith(".chd", ignoreCase = true) || path.endsWith(".iso", ignoreCase = true))
                Storage.open(path, stageCopy = true)?.use { NativeBridge.nativeRaHashFile(it.path) } else null
    override fun onGameRecognized() = Emulator.markAchievementGame()
    override fun onResetRequested() { Emulator.reset(true) }
}
