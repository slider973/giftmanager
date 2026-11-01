package ch.jonathanlemaine.giftmanager.core.budgets

import ch.jonathanlemaine.giftmanager.core.model.DayDate
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

/** Règles d'affichage des budgets (#40), sans dépendance à l'interface — `BudgetMath.swift`. */
object BudgetMath {
    /** Seuil à partir duquel la jauge passe en « presque atteint ». */
    const val WARNING_RATIO = 0.85

    enum class Level { COMFORTABLE, NEARLY_REACHED, OVER }

    fun level(budget: Budget): Level = when {
        budget.isOver -> Level.OVER
        budget.progress >= WARNING_RATIO -> Level.NEARLY_REACHED
        else -> Level.COMFORTABLE
    }

    /**
     * Montant compact : « 180 », « 199,90 », « 1 200 » (espace fine insécable). Séparateurs fixés, indépendants
     * de la locale du téléphone ; arrondi au pair (comme `NumberFormatter`).
     */
    fun number(value: BigDecimal): String {
        val digits = if (value.compareTo(value.setScale(0, RoundingMode.HALF_UP)) == 0) 0 else 2
        val symbols = DecimalFormatSymbols(Locale.ROOT).apply {
            groupingSeparator = ' '
            decimalSeparator = ','
        }
        return DecimalFormat("#,##0", symbols).apply {
            isGroupingUsed = true
            minimumFractionDigits = digits
            maximumFractionDigits = digits
            roundingMode = RoundingMode.HALF_EVEN
        }.format(value)
    }

    fun symbol(currency: String): String = when (val code = currency.uppercase()) {
        "EUR" -> "€"
        "GBP" -> "£"
        "USD" -> "$"
        "CAD" -> "$ CA"
        else -> code
    }

    /** Jauge : « 180 / 200 CHF ». */
    fun gaugeText(spent: BigDecimal, amount: BigDecimal, currency: String): String =
        "${number(spent)} / ${number(amount)} ${symbol(currency)}"

    /** « 20 CHF ». */
    fun amountText(value: BigDecimal, currency: String): String = "${number(value)} ${symbol(currency)}"

    /**
     * Deux budgets comptent-ils (en partie) les mêmes cadeaux ?
     * Ils sont disjoints seulement s'ils visent deux enfants différents ou deux événements différents.
     */
    fun overlap(a: Budget, b: Budget): Boolean {
        if (a.childId != null && b.childId != null && a.childId != b.childId) return false
        if (a.eventId != null && b.eventId != null && a.eventId != b.eventId) return false
        return true
    }

    /** Les budgets peuvent-ils s'additionner sans compter deux fois le même cadeau ? */
    fun areDisjoint(budgets: List<Budget>): Boolean {
        for (index in budgets.indices) {
            for (other in index + 1 until budgets.size) {
                if (overlap(budgets[index], budgets[other])) return false
            }
        }
        return true
    }

    /**
     * Ordre stable de l'écran (`my_budgets()` n'en garantit aucun) : événement le plus proche d'abord, ceux sans
     * date en dernier, puis prénom, devise et identifiant.
     */
    fun sorted(
        budgets: List<Budget>,
        eventDate: (Budget) -> DayDate?,
        childName: (Budget) -> String? = { it.childName },
    ): List<Budget> = budgets.sortedWith(
        compareBy<Budget, DayDate?>(nullsLast()) { eventDate(it) }
            .thenBy(nullsLast(String.CASE_INSENSITIVE_ORDER)) { childName(it) }
            .thenBy { it.currency }
            .thenBy { it.id },
    )
}
