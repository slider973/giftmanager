package ch.jonathanlemaine.giftmanager.core.model

import java.time.Clock
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/** Date sans heure, au format SQL `date` (« yyyy-MM-dd »). */
@Serializable(with = DayDateSerializer::class)
data class DayDate(val date: LocalDate) : Comparable<DayDate> {
    override fun compareTo(other: DayDate): Int = date.compareTo(other.date)

    /** Jours restants avant cette date (négatif si elle est passée). */
    fun daysRemaining(clock: Clock = Clock.systemDefaultZone()): Int =
        ChronoUnit.DAYS.between(LocalDate.now(clock), date).toInt()

    /** Le jour même ou après. */
    fun hasStarted(clock: Clock = Clock.systemDefaultZone()): Boolean = !date.isAfter(LocalDate.now(clock))

    override fun toString(): String = date.toString()

    companion object {
        private const val SQL_DATE_LENGTH = 10

        fun parse(raw: String): DayDate =
            try {
                // Comme iOS : un suffixe horaire éventuel (« 2026-12-25T00:00:00 ») est ignoré.
                DayDate(LocalDate.parse(raw.take(SQL_DATE_LENGTH)))
            } catch (e: java.time.format.DateTimeParseException) {
                throw SerializationException("Date invalide : $raw", e)
            }
    }
}

object DayDateSerializer : KSerializer<DayDate> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("DayDate", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): DayDate = DayDate.parse(decoder.decodeString())

    override fun serialize(encoder: Encoder, value: DayDate) = encoder.encodeString(value.date.toString())
}
