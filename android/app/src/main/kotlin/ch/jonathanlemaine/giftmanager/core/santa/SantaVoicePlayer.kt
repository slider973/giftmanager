package ch.jonathanlemaine.giftmanager.core.santa

/**
 * Joue les répliques pré-générées du Père Noël — `SantaVoicePlayer.swift`. L'audio est livré avec l'application,
 * jamais synthétisé à l'exécution : aucune clé ElevenLabs sur l'appareil, aucune latence réseau, et le contenu
 * prononcé est exactement celui qui a été relu.
 */
interface SantaVoicePlayer {
    val isPlaying: Boolean

    /** Vrai si un fichier audio manquait ou n'a pas pu être lu : l'écran affiche alors le texte seul. */
    val audioUnavailable: Boolean

    /**
     * Appelé quand [isPlaying] ou [audioUnavailable] change sans que la réplique finisse (pause par perte de focus
     * audio, par exemple) ; « Passer » et « Réécouter » restent utilisables.
     */
    var onStateChanged: (() -> Unit)?

    /**
     * Joue la réplique [assetPath] (« audio/accueil.mp3 »). [onFinish] est appelé une fois à la fin, ou tout de suite
     * si l'audio est introuvable ou illisible — la session ne doit jamais rester bloquée. Il n'est pas appelé si la
     * lecture est interrompue par [stop].
     */
    fun play(assetPath: String, onFinish: () -> Unit)

    /** Interrompt la lecture sans appeler `onFinish`. */
    fun stop()

    /** Libère le lecteur ; il ne sert plus ensuite. */
    fun release()
}
