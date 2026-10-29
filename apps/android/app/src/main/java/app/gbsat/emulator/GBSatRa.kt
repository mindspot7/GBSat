package app.gbsat.emulator

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
    override fun hashGame(path: String): String? = Storage.useDisc(path) { NativeBridge.nativeRaHashFile(it.path) }
    override fun onGameRecognized() = Emulator.markAchievementGame()
    override fun onResetRequested() { Emulator.reset(true) }
}
