package ch.jonathanlemaine.giftmanager.core.santa

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Script du Père Noël : répliques et questions — `SantaScript.swift`.
 *
 * Le script est **fermé et versionné** : aucune génération de texte à l'exécution. On sait donc exactement ce que
 * l'enfant va entendre. Le JSON (`ios/GiftManager/Resources/SantaScript`) est partagé avec iOS, sans copie.
 */
@Serializable
data class SantaScript(
    val voix: Voice,
    val repliques: List<Line>,
) {
    @Serializable
    data class Voice(
        val nom: String,
        @SerialName("voice_id") val voiceId: String,
        @SerialName("model_id") val modelId: String,
    )

    @Serializable
    data class Choice(val cle: String, val libelle: String)

    // Toute nouveauté du JSON passe par une valeur par défaut : un champ obligatoire de plus casserait le décodage
    // des scripts déjà livrés.
    @Serializable
    data class Line(
        val id: String,
        val texte: String,
        @SerialName("attend_reponse") val attendReponse: Boolean = false,
        val champ: String? = null,
        val choix: List<Choice>? = null,
        @SerialName("aide_parent") val aideParent: String? = null,
        val facultatif: Boolean = false,
    ) {
        /** Fichier audio pré-généré de la réplique, dans les assets. */
        val audioAsset: String get() = "audio/$id.mp3"
    }

    companion object {
        const val ASSET_NAME = "santa_script_fr.json"

        // Le fichier porte `_commentaire` et `voix.reglages` (réglages de la génération audio) : inutiles ici.
        private val json = Json { ignoreUnknownKeys = true }

        fun parse(text: String): SantaScript = json.decodeFromString(serializer(), text)

        /**
         * Script vide, pour le seul cas où le fichier livré serait introuvable : la session affichera alors un écran
         * « impossible » plutôt que de planter.
         */
        val EMPTY = SantaScript(Voice(nom = "", voiceId = "", modelId = ""), emptyList())
    }
}

/** Ce que l'enfant a exprimé pendant la session, tel que le parent l'a saisi. */
data class SantaWishes(
    val prenom: String = "",
    val activites: Set<String> = emptySet(),
    val enviePrincipale: String = "",
    val preferences: String = "",
    val envieSecondaire: String = "",
) {
    /** Une session n'a d'intérêt que si l'enfant a exprimé au moins une envie. */
    val estExploitable: Boolean get() = enviePrincipale.trim().isNotEmpty()

    /**
     * Requêtes à lancer dans les boutiques, de la plus précise à la plus large. Les préférences (couleur,
     * personnage) affinent la requête principale : « trottinette » devient « trottinette rouge ».
     */
    val requetes: List<String>
        get() {
            val principale = enviePrincipale.trim()
            val prefs = preferences.trim()
            val secondaire = envieSecondaire.trim()
            return buildList {
                if (principale.isNotEmpty()) {
                    if (prefs.isNotEmpty()) add("$principale $prefs")
                    add(principale)
                }
                if (secondaire.isNotEmpty()) add(secondaire)
            }
        }
}

/** D'où vient le script ; `fun interface` pour le remplacer dans les tests. */
fun interface SantaScriptSource {
    /** Script livré, ou [SantaScript.EMPTY] s'il est introuvable ou illisible. */
    fun load(): SantaScript
}
