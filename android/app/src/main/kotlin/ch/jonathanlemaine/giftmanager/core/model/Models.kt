@file:UseSerializers(UuidSerializer::class)

package ch.jonathanlemaine.giftmanager.core.model

import java.time.Clock
import java.time.LocalDate
import java.time.Period
import java.util.UUID
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers

// Modèles alignés sur le schéma Supabase (supabase/migrations) et sur ios/GiftManager/Core/Models.swift.
// Les dates SQL `date` sont transportées en chaîne « yyyy-MM-dd » et exposées via [DayDate].

@Serializable
data class Profile(
    val id: UUID,
    @SerialName("display_name") val displayName: String,
    val country: String,
    val currency: String,
    val onboarded: Boolean,
    /** Rappels d'anniversaire J-30 / J-7 (#43), envoyés par le serveur. */
    @SerialName("notify_birthday_reminders") val notifyBirthdayReminders: Boolean = true,
    /** Rappels d'achat J-30 / J-15 / J-7 / J-2 sur mes propres réservations (#59). */
    @SerialName("notify_purchase_reminders") val notifyPurchaseReminders: Boolean = true,
)

@Serializable
data class FamilyGroup(
    val id: UUID,
    val name: String,
    @SerialName("invite_code") val inviteCode: String,
    @SerialName("created_by") val createdBy: UUID? = null,
)

@Serializable
data class Household(
    val id: UUID,
    val name: String,
    val country: String,
    /** Code permettant à un second parent de rejoindre le foyer. */
    @SerialName("invite_code") val inviteCode: String? = null,
)

/** Partage d'un foyer vers une famille (#60) : un même foyer peut être visible dans plusieurs groupes. */
@Serializable
data class HouseholdGroup(
    @SerialName("household_id") val householdId: UUID,
    @SerialName("group_id") val groupId: UUID,
)

@Serializable
data class HouseholdMember(
    @SerialName("household_id") val householdId: UUID,
    @SerialName("user_id") val userId: UUID,
)

@Serializable
data class GroupMember(
    @SerialName("group_id") val groupId: UUID,
    @SerialName("user_id") val userId: UUID,
)

/** Encodé avec [GiftJson.write] : les optionnels valent `null` (et non absents) pour pouvoir les effacer via upsert. */
@Serializable
data class Child(
    val id: UUID,
    @SerialName("household_id") val householdId: UUID,
    @SerialName("first_name") val firstName: String,
    val birthdate: DayDate? = null,
    @SerialName("avatar_emoji") val avatarEmoji: String? = null,
    @SerialName("avatar_color") val avatarColor: String? = null,
    @SerialName("avatar_url") val avatarUrl: String? = null,
    /** Liste d'un adulte du foyer (#37) : mêmes règles de surprise que pour un enfant. */
    @SerialName("is_adult") val isAdult: Boolean = false,
) {
    fun age(clock: Clock = Clock.systemDefaultZone()): Int? = age(on = LocalDate.now(clock))

    fun age(on: LocalDate): Int? = birthdate?.let { Period.between(it.date, on).years }
}

@Serializable
enum class GiftEventKind {
    @SerialName("christmas") CHRISTMAS,
    @SerialName("birthday") BIRTHDAY,
    @SerialName("other") OTHER,
}

@Serializable
data class GiftEvent(
    val id: UUID,
    /** `null` pour Noël (global) et les anniversaires (portés par leur enfant) — voir #60. */
    @SerialName("group_id") val groupId: UUID? = null,
    val kind: GiftEventKind,
    val title: String,
    @SerialName("event_date") val eventDate: DayDate,
    @SerialName("child_id") val childId: UUID? = null,
    /** Lu seulement : défini par la base à la création. */
    @SerialName("created_by") val createdBy: UUID? = null,
) {
    fun isPast(clock: Clock = Clock.systemDefaultZone()): Boolean = eventDate.date.isBefore(LocalDate.now(clock))

    fun daysRemaining(clock: Clock = Clock.systemDefaultZone()): Int = eventDate.daysRemaining(clock)

    /** Charge utile d'upsert : `group_id` et `child_id` à `null` pour pouvoir les effacer, `created_by` jamais envoyé. */
    fun toUpsert(): GiftEventUpsert = GiftEventUpsert(id, groupId, kind, title, eventDate, childId)
}

/** Ce qui est envoyé à la base pour un événement (voir [GiftEvent.toUpsert]). */
@Serializable
data class GiftEventUpsert(
    val id: UUID,
    @SerialName("group_id") val groupId: UUID?,
    val kind: GiftEventKind,
    val title: String,
    @SerialName("event_date") val eventDate: DayDate,
    @SerialName("child_id") val childId: UUID?,
)
