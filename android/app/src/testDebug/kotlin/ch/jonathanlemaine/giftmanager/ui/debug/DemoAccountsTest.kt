package ch.jonathanlemaine.giftmanager.ui.debug

import org.junit.Assert.assertEquals
import org.junit.Test

class DemoAccountsTest {
    @Test
    fun `les trois comptes du seed sont proposes dans l'ordre`() {
        assertEquals(listOf("Alice", "Bob", "Carol"), DemoAccounts.all.map { it.label })
    }

    @Test
    fun `les identifiants suivent le seed supabase`() {
        val alice = DemoAccounts.all.first()
        assertEquals("alice@demo.local", alice.email)
        assertEquals("demo1234", alice.password)
        assertEquals(listOf("alice@demo.local", "bob@demo.local", "carol@demo.local"), DemoAccounts.all.map { it.email })
    }
}
