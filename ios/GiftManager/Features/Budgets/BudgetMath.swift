import Foundation

/// Règles d'affichage des budgets (#40), sans dépendance à l'interface.
enum BudgetMath {
    /// Seuil à partir duquel la jauge passe en « presque atteint ».
    static let warningRatio = 0.85

    enum Level: Equatable { case comfortable, nearlyReached, over }

    static func level(_ budget: Budget) -> Level {
        if budget.isOver { return .over }
        return budget.progress >= warningRatio ? .nearlyReached : .comfortable
    }

    /// Montant compact : « 180 », « 199,90 ».
    static func number(_ value: Decimal) -> String {
        var input = value
        var whole = Decimal()
        NSDecimalRound(&whole, &input, 0, .plain)
        let digits = whole == value ? 0 : 2
        // Séparateurs fixés : ceux de « fr_CH » changent selon la version d'iOS (’, ', espace fine).
        let formatter = NumberFormatter()
        formatter.numberStyle = .decimal
        formatter.groupingSeparator = "\u{202F}"
        formatter.decimalSeparator = ","
        formatter.minimumFractionDigits = digits
        formatter.maximumFractionDigits = digits
        return formatter.string(from: value as NSDecimalNumber) ?? "\(value)"
    }

    static func symbol(_ currency: String) -> String {
        switch currency.uppercased() {
        case "EUR": "€"
        case "GBP": "£"
        case "USD": "$"
        case "CAD": "$ CA"
        default: currency.uppercased()
        }
    }

    /// Jauge : « 180 / 200 CHF ».
    static func gaugeText(spent: Decimal, amount: Decimal, currency: String) -> String {
        "\(number(spent)) / \(number(amount)) \(symbol(currency))"
    }

    /// « 20 CHF ».
    static func amountText(_ value: Decimal, currency: String) -> String {
        "\(number(value)) \(symbol(currency))"
    }

    /// Deux budgets comptent-ils (en partie) les mêmes cadeaux ?
    /// Ils sont disjoints seulement s'ils visent deux enfants différents ou deux événements différents.
    static func overlap(_ a: Budget, _ b: Budget) -> Bool {
        if let ca = a.childId, let cb = b.childId, ca != cb { return false }
        if let ea = a.eventId, let eb = b.eventId, ea != eb { return false }
        return true
    }

    /// Les budgets peuvent-ils s'additionner sans compter deux fois le même cadeau ?
    static func areDisjoint(_ budgets: [Budget]) -> Bool {
        for (index, a) in budgets.enumerated() {
            for b in budgets[(index + 1)...] where overlap(a, b) { return false }
        }
        return true
    }
}
