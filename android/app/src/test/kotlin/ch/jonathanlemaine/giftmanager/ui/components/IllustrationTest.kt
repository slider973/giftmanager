package ch.jonathanlemaine.giftmanager.ui.components

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IllustrationTest {
    // Gradle exécute les tests depuis android/app.
    private val dir = File("src/main/res/drawable-nodpi")
    private val webps get() = dir.listFiles { f -> f.extension == "webp" }!!.sortedBy { it.name }

    @Test fun tenIllustrations() = assertEquals(10, Illustration.entries.size)

    @Test fun everyIllustrationHasAWebpFile() {
        Illustration.entries.forEach {
            assertTrue("fichier manquant pour $it", File(dir, it.name.lowercase() + ".webp").isFile)
            assertTrue("ressource non résolue pour $it", it.res != 0)
        }
    }

    @Test fun noOrphanWebp() =
        assertEquals(Illustration.entries.map { it.name.lowercase() }.sorted(), webps.map { it.nameWithoutExtension })

    @Test fun matchesIosAssets() {
        val ios = File("../../ios/GiftManager/Resources/Illustrations.xcassets")
            .listFiles { f -> f.name.endsWith(".imageset") }!!.map { it.name.removeSuffix(".imageset") }.sorted()
        assertEquals(ios, webps.map { it.nameWithoutExtension })
    }

    @Test fun totalSizeUnderBudget() {
        val total = webps.sumOf { it.length() }
        assertTrue("taille cumulée $total octets", total < 1_500_000)
    }

    @Test fun filesAreWebp() = webps.forEach {
        val head = it.inputStream().use { s -> s.readNBytes(12) }
        assertEquals("RIFF", String(head, 0, 4))
        assertEquals("WEBP", String(head, 8, 4))
    }
}
