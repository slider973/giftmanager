package ch.jonathanlemaine.giftmanager.core.santa

import java.util.UUID

/**
 * Ce dont la session du Père Noël a besoin du dépôt distant. Interface étroite, pour que le ViewModel soit testable
 * sans réseau : une idée de cadeau n'est créée qu'après validation explicite du parent.
 */
fun interface SantaSessionActions {
    /** Crée une idée de cadeau pour un enfant, à partir d'une suggestion validée ; lève en cas d'échec. */
    suspend fun createGiftIdea(childId: UUID, title: String, link: String?)
}
