package ch.jonathanlemaine.giftmanager.core.model

import java.math.BigDecimal
import java.time.Instant
import java.util.UUID
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonUnquotedLiteral

// Sérialiseurs des types Java utilisés par les modèles (équivalents des conformances Codable de Foundation).

/** UUID transporté en chaîne. */
object UuidSerializer : KSerializer<UUID> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("UUID", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): UUID {
        val raw = decoder.decodeString()
        return try {
            UUID.fromString(raw)
        } catch (e: IllegalArgumentException) {
            throw SerializationException("UUID invalide : $raw", e)
        }
    }

    override fun serialize(encoder: Encoder, value: UUID) = encoder.encodeString(value.toString())
}

/**
 * Montant décimal (`numeric` SQL). PostgREST renvoie un nombre JSON, mais une chaîne est acceptée aussi.
 * Encodé comme un nombre JSON, sans perte de précision.
 */
object DecimalSerializer : KSerializer<BigDecimal> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("Decimal", PrimitiveKind.DOUBLE)

    override fun deserialize(decoder: Decoder): BigDecimal {
        val primitive = (decoder as? JsonDecoder)?.decodeJsonElement() as? JsonPrimitive
            ?: throw SerializationException("Montant attendu (nombre ou chaîne)")
        return primitive.content.toBigDecimalOrNull()
            ?: throw SerializationException("Montant invalide : ${primitive.content}")
    }

    override fun serialize(encoder: Encoder, value: BigDecimal) {
        if (encoder is JsonEncoder) {
            encoder.encodeJsonElement(JsonUnquotedLiteral(value.toPlainString()))
        } else {
            encoder.encodeString(value.toPlainString())
        }
    }
}

/** Horodatage `timestamptz`, avec ou sans fractions de seconde (voir [Timestamp]). */
object InstantSerializer : KSerializer<Instant> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("Instant", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): Instant {
        val raw = decoder.decodeString()
        return Timestamp.parse(raw) ?: throw SerializationException("Horodatage invalide : $raw")
    }

    override fun serialize(encoder: Encoder, value: Instant) = encoder.encodeString(value.toString())
}
