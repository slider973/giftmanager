package ch.jonathanlemaine.giftmanager.push

class FakePushPrefs : PushPrefs {
    var registered: RegisteredToken? = null
    var asked = false
    var prompted = false

    override suspend fun registeredToken() = registered

    override suspend fun setRegisteredToken(value: RegisteredToken?) {
        registered = value
    }

    override suspend fun permissionAsked() = asked

    override suspend fun setPermissionAsked() {
        asked = true
    }

    override suspend fun promptShown() = prompted

    override suspend fun setPromptShown() {
        prompted = true
    }
}
