package ch.jonathanlemaine.giftmanager.ui.santa

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import ch.jonathanlemaine.giftmanager.core.santa.SantaVoicePlayer

/**
 * [SantaVoicePlayer] sur Media3/ExoPlayer. À créer et utiliser depuis le fil principal. Le lecteur ExoPlayer n'est
 * construit qu'à la première lecture.
 */
class Media3SantaVoicePlayer(context: Context) : SantaVoicePlayer {
    private val appContext = context.applicationContext
    private var player: ExoPlayer? = null
    private var onFinish: (() -> Unit)? = null

    override var isPlaying: Boolean = false
        private set

    override var audioUnavailable: Boolean = false
        private set

    override var onStateChanged: (() -> Unit)? = null

    private val listener = object : Player.Listener {
        // Perte de focus audio (appel, autre app) : pause sans erreur ni fin de lecture.
        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) = syncPlaying()

        override fun onPlaybackSuppressionReasonChanged(playbackSuppressionReason: Int) = syncPlaying()

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_ENDED) finish(failed = false)
        }

        override fun onPlayerError(error: PlaybackException) = finish(failed = true)
    }

    override fun play(assetPath: String, onFinish: () -> Unit) {
        stop()
        // Fichier manquant : on le sait tout de suite, sans attendre l'erreur asynchrone d'ExoPlayer.
        val exists = try {
            appContext.assets.open(assetPath).close()
            true
        } catch (_: Exception) {
            false
        }
        if (!exists) {
            audioUnavailable = true
            onFinish()
            return
        }
        this.onFinish = onFinish
        try {
            val exo = player ?: createPlayer().also { player = it }
            audioUnavailable = false
            isPlaying = true
            exo.setMediaItem(MediaItem.fromUri("asset:///$assetPath"))
            exo.prepare()
            exo.play()
        } catch (_: Exception) {
            // Un défaut de lecture ne doit pas interrompre la session : l'écran montre le texte, le parent le lit.
            finish(failed = true)
        }
    }

    override fun stop() {
        onFinish = null
        isPlaying = false
        player?.let {
            it.stop()
            it.clearMediaItems()
        }
    }

    override fun release() {
        onFinish = null
        onStateChanged = null
        isPlaying = false
        player?.removeListener(listener)
        player?.release()
        player = null
    }

    private fun createPlayer(): ExoPlayer =
        ExoPlayer.Builder(appContext).build().also {
            // Parole (et non musique), avec gestion automatique du focus audio : un appel ou une autre app coupe la voix.
            it.setAudioAttributes(
                AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_SPEECH).build(),
                /* handleAudioFocus = */ true,
            )
            it.addListener(listener)
        }

    /** [isPlaying] suit la pause du lecteur tant qu'une réplique est en cours. */
    private fun syncPlaying() {
        val exo = player ?: return
        if (onFinish == null) return
        val playing = exo.playWhenReady && exo.playbackSuppressionReason == Player.PLAYBACK_SUPPRESSION_REASON_NONE
        if (playing == isPlaying) return
        isPlaying = playing
        onStateChanged?.invoke()
    }

    private fun finish(failed: Boolean) {
        val callback = onFinish ?: return
        onFinish = null
        isPlaying = false
        if (failed) audioUnavailable = true
        callback()
    }
}
