package ch.jonathanlemaine.giftmanager.ui.theme

import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Les couleurs Android doivent rester identiques aux colorsets iOS (source de vérité). */
class PaletteParityTest {
    // Gradle exécute les tests depuis android/app.
    private val themeDir = File("../../ios/GiftManager/Resources/Assets.xcassets/Theme")

    private fun component(c: JsonObject, key: String): Long {
        val raw = c.getValue(key).jsonPrimitive.content
        return if (raw.startsWith("0x")) raw.drop(2).toLong(16) else (raw.toDouble() * 255).toLong()
    }

    /** Retourne (clair, sombre) en ARGB opaque pour un token. */
    private fun read(name: String): Pair<Long, Long> {
        val json = Json.parseToJsonElement(File(themeDir, "$name.colorset/Contents.json").readText())
        var light: Long? = null
        var dark: Long? = null
        (json.jsonObject.getValue("colors") as JsonArray).forEach { entry ->
            val o = entry.jsonObject
            val comps = o.getValue("color").jsonObject.getValue("components").jsonObject
            assertEquals("$name : alpha", 1.0, comps.getValue("alpha").jsonPrimitive.content.toDouble(), 0.0)
            val argb = 0xFF000000 or (component(comps, "red") shl 16) or (component(comps, "green") shl 8) or component(comps, "blue")
            if (o["appearances"]?.jsonArray?.isNotEmpty() == true) dark = argb else light = argb
        }
        return checkNotNull(light) { "$name : valeur claire absente" } to checkNotNull(dark) { "$name : valeur sombre absente" }
    }

    @Test fun everyTokenMatchesIosColorset() {
        assertTrue("dossier iOS introuvable : ${themeDir.absolutePath}", themeDir.isDirectory)
        ThemeTokens.names.forEach { name ->
            val (light, dark) = read(name)
            assertEquals("$name (clair)", light, ThemeTokens.Light.byName(name))
            assertEquals("$name (sombre)", dark, ThemeTokens.Dark.byName(name))
        }
    }

    @Test fun noIosColorsetIsMissingOnAndroid() {
        val ios = themeDir.listFiles { f -> f.name.endsWith(".colorset") }!!.map { it.name.removeSuffix(".colorset") }.toSet()
        assertEquals(ios, ThemeTokens.names.toSet())
    }
}
