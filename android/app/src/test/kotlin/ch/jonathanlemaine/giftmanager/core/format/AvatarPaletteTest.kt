package ch.jonathanlemaine.giftmanager.core.format

import ch.jonathanlemaine.giftmanager.ui.theme.Pastel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Même palette que `AvatarPalette` d'iOS (`Core/Formatting.swift`). */
class AvatarPaletteTest {
    @Test fun twelveEmojisInIosOrder() {
        assertEquals(listOf("🦖", "🦄", "⚽️", "🐻", "🐱", "🚀", "🌸", "🦊", "🐼", "🎨", "🏰", "🧸"), AvatarPalette.emojis)
    }

    @Test fun fivePastelColors() {
        assertEquals(listOf("pastelPink", "pastelMint", "pastelBlue", "pastelPeach", "pastelLavender"), AvatarPalette.colors)
    }

    @Test fun defaultsAreFirstEmojiAndMint() {
        assertEquals("🦖", AvatarPalette.defaultEmoji)
        assertEquals("pastelMint", AvatarPalette.defaultColor)
    }

    @Test fun everyColorIsAKnownPastelToken() {
        assertTrue(AvatarPalette.colors.all { Pastel.named(it) != null })
        assertEquals(AvatarPalette.colors, Pastel.entries.map { it.tokenName })
    }
}
