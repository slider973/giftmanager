package ch.jonathanlemaine.giftmanager.widget

import ch.jonathanlemaine.giftmanager.ui.theme.ThemeTokens
import ch.jonathanlemaine.giftmanager.ui.theme.contrastRatio
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Les couleurs XML du widget (layouts de chargement / d'aperçu, formes de repli avant Android 12) doivent rester
 * le reflet des tokens, et les textes du widget tenir l'AA sur son fond dans les deux apparences.
 */
class WidgetColorResourcesTest {
    // Gradle exécute les tests depuis android/app.
    private val res = File("src/main/res")

    private val tokens = mapOf(
        "widget_background" to "background",
        "widget_primary" to "primary",
        "widget_text_primary" to "textPrimary",
        "widget_text_secondary" to "textSecondary",
        "widget_tile_mint" to "pastelMint",
        "widget_tile_pink" to "pastelPink",
        "widget_tile_peach" to "pastelPeach",
    )

    private fun colors(folder: String): Map<String, Long> =
        Regex("""<color name="(\w+)">#([0-9A-Fa-f]{6})</color>""")
            .findAll(File(res, "$folder/colors.xml").readText())
            .associate { it.groupValues[1] to (0xFF000000 or it.groupValues[2].toLong(16)) }

    @Test fun xmlColorsMirrorTokens() {
        val light = colors("values")
        val dark = colors("values-night")
        tokens.forEach { (resource, token) ->
            assertEquals("$resource (clair)", ThemeTokens.Light.byName(token), light[resource])
            assertEquals("$resource (sombre)", ThemeTokens.Dark.byName(token), dark[resource])
        }
    }

    @Test fun widgetTextIsAaOnBackground() {
        listOf(ThemeTokens.Light, ThemeTokens.Dark).forEach { t ->
            listOf(t.primary, t.textPrimary, t.textSecondary).forEach { fg ->
                assertTrue(contrastRatio(fg, t.background) >= 4.5)
            }
        }
    }
}
