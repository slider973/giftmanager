package ch.jonathanlemaine.giftmanager.core

import ch.jonathanlemaine.giftmanager.core.model.GiftJson
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.serialization.decodeFromString

/** Fixtures JSON partagées : supabase/fixtures/json (ajoutées aux ressources de test par app/build.gradle.kts). */
object Fixtures {
    fun load(name: String): String =
        checkNotNull(Fixtures::class.java.classLoader!!.getResourceAsStream(name)) { "Fixture introuvable : $name" }
            .use { it.readBytes().toString(Charsets.UTF_8) }

    inline fun <reified T> decode(name: String): T = GiftJson.read.decodeFromString<T>(load(name))

    /** Horloge figée au 15 octobre 2026. */
    val clock: Clock = Clock.fixed(Instant.parse("2026-10-15T10:00:00Z"), ZoneOffset.UTC)
}
