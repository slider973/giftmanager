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
