import Foundation

/// Déduit la boutique, le pays et la devise à partir du domaine d'un lien.
enum StoreCatalog {
    struct Store: Equatable {
        let name: String
        let country: String?
        let currency: String?
    }

    /// Domaine (sans « www. ») → boutique. Les domaines génériques (.com) n'ont pas de pays.
    private static let known: [String: Store] = [
        "amazon.fr": Store(name: "Amazon", country: "FR", currency: "EUR"),
        "amazon.de": Store(name: "Amazon", country: "DE", currency: "EUR"),
        "amazon.it": Store(name: "Amazon", country: "IT", currency: "EUR"),
        "amazon.es": Store(name: "Amazon", country: "ES", currency: "EUR"),
        "amazon.co.uk": Store(name: "Amazon", country: "GB", currency: "GBP"),
        "amazon.com": Store(name: "Amazon", country: "US", currency: "USD"),
        "amazon.com.be": Store(name: "Amazon", country: "BE", currency: "EUR"),
        "galaxus.ch": Store(name: "Galaxus", country: "CH", currency: "CHF"),
        "galaxus.de": Store(name: "Galaxus", country: "DE", currency: "EUR"),
        "galaxus.fr": Store(name: "Galaxus", country: "FR", currency: "EUR"),
        "digitec.ch": Store(name: "Digitec", country: "CH", currency: "CHF"),
        "manor.ch": Store(name: "Manor", country: "CH", currency: "CHF"),
        "brack.ch": Store(name: "Brack", country: "CH", currency: "CHF"),
        "interdiscount.ch": Store(name: "Interdiscount", country: "CH", currency: "CHF"),
        "fust.ch": Store(name: "Fust", country: "CH", currency: "CHF"),
        "ricardo.ch": Store(name: "Ricardo", country: "CH", currency: "CHF"),
        "franz-carl-weber.ch": Store(name: "Franz Carl Weber", country: "CH", currency: "CHF"),
        "fcw.ch": Store(name: "Franz Carl Weber", country: "CH", currency: "CHF"),
        "smythstoys.com": Store(name: "Smyths Toys", country: nil, currency: nil),
        "mediamarkt.ch": Store(name: "MediaMarkt", country: "CH", currency: "CHF"),
        "mediamarkt.de": Store(name: "MediaMarkt", country: "DE", currency: "EUR"),
        "fnac.com": Store(name: "Fnac", country: "FR", currency: "EUR"),
        "fnac.ch": Store(name: "Fnac", country: "CH", currency: "CHF"),
        "darty.com": Store(name: "Darty", country: "FR", currency: "EUR"),
        "boulanger.com": Store(name: "Boulanger", country: "FR", currency: "EUR"),
        "cdiscount.com": Store(name: "Cdiscount", country: "FR", currency: "EUR"),
        "joueclub.fr": Store(name: "JouéClub", country: "FR", currency: "EUR"),
        "king-jouet.com": Store(name: "King Jouet", country: "FR", currency: "EUR"),
        "lagranderecre.fr": Store(name: "La Grande Récré", country: "FR", currency: "EUR"),
        "oxybul.com": Store(name: "Oxybul", country: "FR", currency: "EUR"),
        "auchan.fr": Store(name: "Auchan", country: "FR", currency: "EUR"),
        "carrefour.fr": Store(name: "Carrefour", country: "FR", currency: "EUR"),
        "leclerc.com": Store(name: "E.Leclerc", country: "FR", currency: "EUR"),
        "decathlon.fr": Store(name: "Decathlon", country: "FR", currency: "EUR"),
        "decathlon.ch": Store(name: "Decathlon", country: "CH", currency: "CHF"),
        "zalando.fr": Store(name: "Zalando", country: "FR", currency: "EUR"),
        "zalando.ch": Store(name: "Zalando", country: "CH", currency: "CHF"),
        "ikea.com": Store(name: "IKEA", country: nil, currency: nil),
        "lego.com": Store(name: "LEGO", country: nil, currency: nil),
        "nike.com": Store(name: "Nike", country: nil, currency: nil),
        "apple.com": Store(name: "Apple", country: nil, currency: nil),
        "etsy.com": Store(name: "Etsy", country: nil, currency: nil),
        "vinted.fr": Store(name: "Vinted", country: "FR", currency: "EUR"),
        "leboncoin.fr": Store(name: "leboncoin", country: "FR", currency: "EUR"),
    ]

    private static let currencyByCountry: [String: String] = [
        "CH": "CHF", "LI": "CHF", "FR": "EUR", "DE": "EUR", "IT": "EUR", "ES": "EUR", "BE": "EUR",
        "LU": "EUR", "AT": "EUR", "NL": "EUR", "PT": "EUR", "IE": "EUR", "GB": "GBP", "US": "USD", "CA": "CAD",
    ]

    static func currency(for country: String?) -> String? {
        country.flatMap { currencyByCountry[$0] }
    }

    static func store(for urlString: String) -> Store? {
        guard let host = URL(string: urlString.trimmingCharacters(in: .whitespacesAndNewlines))?.host()?.lowercased() else {
            return nil
        }
        let domain = host.hasPrefix("www.") ? String(host.dropFirst(4)) : host
        if let store = known[domain] { return store }
        // Sous-domaines (ex. fr.shop.example.ch) : on retente avec les deux derniers niveaux.
        let parts = domain.split(separator: ".")
        if parts.count > 2, let store = known[parts.suffix(2).joined(separator: ".")] { return store }

        // Inconnu : nom tiré du domaine, pays tiré de l'extension.
        let name = parts.dropLast().last.map { $0.prefix(1).uppercased() + $0.dropFirst() } ?? domain
        let tld = parts.last.map { String($0).uppercased() }
        let country = tld.flatMap { currencyByCountry[$0] != nil ? $0 : nil }
        return Store(name: name, country: country, currency: currency(for: country))
    }

    /// Ordonne les liens : ceux du pays du lecteur d'abord.
    static func sorted(_ links: [ItemLink], preferredCountry: String?) -> [ItemLink] {
        links.sorted { a, b in
            let aMatch = a.country == preferredCountry, bMatch = b.country == preferredCountry
            if aMatch != bMatch { return aMatch }
            return (a.store ?? "") < (b.store ?? "")
        }
    }
}
