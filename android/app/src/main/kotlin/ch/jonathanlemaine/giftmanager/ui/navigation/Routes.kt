package ch.jonathanlemaine.giftmanager.ui.navigation

import kotlinx.serialization.Serializable

/** Destinations typées de la navigation Compose. */
@Serializable
data object Home

/** Galerie du design system : enregistrée seulement si le build la contient (debug). */
@Serializable
data object DebugGallery
