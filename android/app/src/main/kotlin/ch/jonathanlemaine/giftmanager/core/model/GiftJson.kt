package ch.jonathanlemaine.giftmanager.core.model

import kotlinx.serialization.json.Json

/** Configurations JSON de l'app, calquées sur le comportement des `JSONDecoder` / `JSONEncoder` iOS. */
object GiftJson {
    /**
     * Lecture tolérante : champs inconnus ignorés, champs absents ou `null` ramenés à la valeur par défaut
     * du modèle (équivalent des `decodeIfPresent ?? x` côté iOS).
     */
    val read: Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        coerceInputValues = true
    }

    /**
     * Écriture pour l'upsert : les optionnels valent `null` explicite (et non absents), pour pouvoir
     * effacer une valeur. `created_by` est exclu en amont (voir [GiftEventUpsert]).
     */
    val write: Json = Json {
        encodeDefaults = true
        explicitNulls = true
    }
}
