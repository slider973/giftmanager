import Foundation

enum Formatting {
    static let longDate: DateFormatter = {
        let f = DateFormatter()
        f.locale = Locale(identifier: "fr_FR")
        f.dateFormat = "d MMMM yyyy"
        return f
    }()

    static func dateText(_ day: DayDate) -> String {
        longDate.string(from: day.localDate)
    }

    static func ageText(_ age: Int?) -> String? {
        guard let age else { return nil }
        return age <= 1 ? "\(age) an" : "\(age) ans"
    }

    static func countdownText(days: Int) -> String {
        switch days {
        case ..<0: "Passé"
        case 0: "Aujourd'hui"
        case 1: "Demain"
        default: "Dans \(days) jours"
        }
    }
}

/// Pays proposés dans les sélecteurs (Suisse et France en tête).
enum Countries {
    struct Country: Identifiable, Hashable {
        let code: String
        let name: String
        var id: String { code }
        var flag: String {
            code.unicodeScalars.compactMap { UnicodeScalar(127_397 + $0.value) }.map(String.init).joined()
        }
    }

    static let all: [Country] = [
        Country(code: "CH", name: "Suisse"),
        Country(code: "FR", name: "France"),
        Country(code: "BE", name: "Belgique"),
        Country(code: "LU", name: "Luxembourg"),
        Country(code: "DE", name: "Allemagne"),
        Country(code: "AT", name: "Autriche"),
        Country(code: "IT", name: "Italie"),
        Country(code: "ES", name: "Espagne"),
        Country(code: "PT", name: "Portugal"),
        Country(code: "NL", name: "Pays-Bas"),
        Country(code: "GB", name: "Royaume-Uni"),
        Country(code: "IE", name: "Irlande"),
        Country(code: "US", name: "États-Unis"),
        Country(code: "CA", name: "Canada"),
    ]

    static let currencies = ["CHF", "EUR", "GBP", "USD", "CAD"]

    static func name(_ code: String?) -> String {
        all.first { $0.code == code }?.name ?? (code ?? "—")
    }
}

/// Couleurs d'avatar proposées pour les enfants (noms des tokens pastel du design system).
enum AvatarPalette {
    static let colors = ["pastelPink", "pastelMint", "pastelBlue", "pastelPeach", "pastelLavender"]
    static let emojis = ["🦖", "🦄", "⚽️", "🐻", "🐱", "🚀", "🌸", "🦊", "🐼", "🎨", "🏰", "🧸"]
}

extension String {
    var trimmed: String { trimmingCharacters(in: .whitespacesAndNewlines) }
}
