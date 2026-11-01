package ch.jonathanlemaine.giftmanager.core.childmode

/** Vérification de l'adulte à la sortie du mode enfant : empreinte, visage ou code de l'appareil. */
interface ParentAuthenticator {
    /** Une empreinte, un visage ou un code est configuré sur l'appareil. */
    fun isAvailable(): Boolean

    /**
     * `true` si l'adulte est vérifié, ou si l'appareil n'a aucun code configuré (l'appui long reste alors la seule
     * protection). `false` si l'authentification échoue ou est annulée.
     */
    suspend fun authenticate(): Boolean
}
