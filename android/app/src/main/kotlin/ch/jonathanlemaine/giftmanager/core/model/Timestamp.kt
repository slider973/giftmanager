package ch.jonathanlemaine.giftmanager.core.model

import java.time.Instant
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException

/** Horodatages PostgREST (`timestamptz`), avec ou sans fractions de seconde. */
object Timestamp {
    private val fractionalSeconds = Regex("""\.\d+""")

    fun parse(raw: String): Instant? =
        parseOrNull(raw)
            // Fractions inattendues (« .123456789012 ») : on les retire plutôt que d'échouer.
            ?: parseOrNull(raw.replace(fractionalSeconds, ""))

    private fun parseOrNull(raw: String): Instant? =
        try {
            OffsetDateTime.parse(raw).toInstant()
        } catch (_: DateTimeParseException) {
            null
        }
}
