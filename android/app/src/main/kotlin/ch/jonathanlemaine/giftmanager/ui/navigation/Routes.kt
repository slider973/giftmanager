package ch.jonathanlemaine.giftmanager.ui.navigation

import kotlinx.serialization.Serializable

/** Destinations typées de la navigation Compose de l'écran principal (une par onglet). */

/** Onglet Accueil : graphe imbriqué, prêt à recevoir les écrans événement / enfant / cadeau (#72, #73). */
@Serializable
data object HomeGraph

@Serializable
data object FamilyHome

@Serializable
data object Search

/** « Mes achats » à la place de « Notifications » : on suit iOS (#74). */
@Serializable
data object Purchases

@Serializable
data object ProfileTab

/** Galerie du design system : enregistrée seulement si le build la contient (debug). */
@Serializable
data object DebugGallery

// region Écrans de l'onglet Accueil (graphe HomeGraph). Les identifiants sont des chaînes UUID : un type
// de navigation natif n'existe pas pour `UUID`.

/** Fiche d'un événement (#72). */
@Serializable
data class EventDetailRoute(val eventId: String)

/** Ajout (`childId == null`), liste d'adulte (`adult`) ou modification d'un enfant (#72). */
@Serializable
data class ChildEditorRoute(val childId: String? = null, val adult: Boolean = false)

/** Création (`eventId == null`) ou modification d'un événement (#72). */
@Serializable
data class EventEditorRoute(val eventId: String? = null)

/**
 * Liste de cadeaux d'un enfant, dans le contexte d'un événement (`null` : sans filtre). `readOnly` pour un
 * événement passé. La signature est figée : #73 remplace l'écran sans changer la route.
 */
@Serializable
data class ChildGiftsRoute(val childId: String, val eventId: String?, val readOnly: Boolean)

// endregion
