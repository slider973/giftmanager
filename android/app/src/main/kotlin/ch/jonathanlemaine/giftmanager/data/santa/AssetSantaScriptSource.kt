package ch.jonathanlemaine.giftmanager.data.santa

import android.content.res.AssetManager
import ch.jonathanlemaine.giftmanager.core.santa.SantaScript
import ch.jonathanlemaine.giftmanager.core.santa.SantaScriptSource

/** Lit le script livré dans les assets de l'APK (`SantaScript.load()` iOS) ; tout échec donne un script vide. */
class AssetSantaScriptSource(private val assets: AssetManager) : SantaScriptSource {
    override fun load(): SantaScript =
        try {
            SantaScript.parse(assets.open(SantaScript.ASSET_NAME).bufferedReader(Charsets.UTF_8).use { it.readText() })
        } catch (_: Exception) {
            SantaScript.EMPTY
        }
}
