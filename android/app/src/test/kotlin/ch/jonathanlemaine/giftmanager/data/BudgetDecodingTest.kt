package ch.jonathanlemaine.giftmanager.data

import ch.jonathanlemaine.giftmanager.core.budgets.Budget
import ch.jonathanlemaine.giftmanager.core.model.GiftJson
import kotlinx.serialization.decodeFromString
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Décode les budgets comme le fait `SupabaseBudgetRepository` (sérialiseur résolu à la compilation, depuis un
 * autre fichier). Garde-fou : un companion privé sur une classe @Serializable faisait planter l'onglet Mes achats
 * (IllegalAccessError), ce que le décodage par réflexion des fixtures ne révélait pas.
 */
class BudgetDecodingTest {
    @Test fun budgetsDecodeFromAnotherFile() {
        val json = """[{"id":"00000000-0000-0000-0000-0000000000b1","child_id":null,"child_name":null,
            "event_id":"00000000-0000-0000-0000-0000000000e1","event_title":"Noël 2026",
            "amount":200.00,"currency":"CHF","spent":180.5}]"""
        val budgets = GiftJson.read.decodeFromString<List<Budget>>(json)
        assertEquals("Noël 2026", budgets.single().scopeTitle)
        assertEquals(0, budgets.single().spent.compareTo("180.5".toBigDecimal()))
    }
}
