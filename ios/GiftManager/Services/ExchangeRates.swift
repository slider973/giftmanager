import Foundation
import Observation

// Conversion de devises indicative (#38).
//
// Taux quotidiens de la BCE (via Frankfurter, gratuit et sans clé), mis en cache sur
// l'appareil : hors ligne, le dernier taux connu reste utilisé. La conversion ne remplace
// jamais le prix d'origine : elle s'affiche à côté, précédée de « ≈ » et marquée indicative.

/// Taux de change d'une journée, exprimés pour 1 unité de la devise de base.
struct ExchangeRates: Codable, Equatable, Sendable {
    /// Devise de base (EUR pour la BCE).
    let base: String
    /// Jour de publication des taux, « yyyy-MM-dd ».
    let date: String
    /// Unités de chaque devise pour 1 unité de `base`.
    let rates: [String: Decimal]
    /// Moment où l'appareil a récupéré ces taux.
    var fetchedAt: Date

    func rate(for currency: String) -> Decimal? {
        let code = currency.uppercased()
        if code == base.uppercased() { return 1 }
        guard let rate = rates[code], rate > 0 else { return nil }
        return rate
    }

    /// Montant converti, ou `nil` si l'un des deux taux manque. Même devise → montant inchangé.
    func convert(_ amount: Decimal, from: String, to: String) -> Decimal? {
        if from.uppercased() == to.uppercased() { return amount }
        guard let fromRate = rate(for: from), let toRate = rate(for: to) else { return nil }
        return amount / fromRate * toRate
    }

    /// Date de publication lisible (« 2 octobre »), pour la mention « taux BCE du … ».
    var publicationText: String? {
        let parser = DateFormatter()
        parser.locale = Locale(identifier: "en_US_POSIX")
        parser.timeZone = TimeZone(identifier: "UTC")
        parser.dateFormat = "yyyy-MM-dd"
        guard let day = parser.date(from: date) else { return nil }
        let out = DateFormatter()
        out.locale = Locale(identifier: "fr_FR")
        out.timeZone = TimeZone(identifier: "UTC")
        out.dateFormat = "d MMMM"
        return out.string(from: day)
    }
}

// MARK: - Montants indicatifs

enum IndicativePrice {
    /// Arrondi d'affichage : à l'unité dès 10, au centime en dessous (un ≈ 4,80 € reste utile).
    static func rounded(_ value: Decimal) -> Decimal {
        var input = value
        var result = Decimal()
        NSDecimalRound(&result, &input, value.magnitude < 10 ? 2 : 0, .plain)
        return result
    }

    /// Montant seul, sans le « ≈ » : « 147 € », « 214 CHF ».
    static func amountText(_ value: Decimal, currency: String) -> String {
        let code = currency.uppercased()
        let rounded = rounded(value)
        let digits = value.magnitude < 10 ? 2 : 0
        let locale = Locale(identifier: code == "CHF" ? "fr_CH" : "fr_FR")
        return rounded.formatted(.currency(code: code).locale(locale).precision(.fractionLength(digits)))
    }

    /// « ≈ 147 € ».
    static func text(_ value: Decimal, currency: String) -> String {
        "≈ " + amountText(value, currency: currency)
    }

    /// Lecture VoiceOver : « environ 147 €, conversion indicative ».
    static func spokenText(_ value: Decimal, currency: String) -> String {
        "environ \(amountText(value, currency: currency)), conversion indicative"
    }
}

// MARK: - Source et cache

protocol ExchangeRateSource: Sendable {
    func latest() async throws -> ExchangeRates
}

/// Taux de référence de la BCE, via Frankfurter (gratuit, sans clé, publiés chaque jour ouvré).
struct FrankfurterRateSource: ExchangeRateSource {
    var url = URL(string: "https://api.frankfurter.dev/v1/latest?base=EUR")!
    var session: URLSession = .shared

    private struct Payload: Decodable {
        let base: String
        let date: String
        let rates: [String: Double]
    }

    func latest() async throws -> ExchangeRates {
        var request = URLRequest(url: url, timeoutInterval: 10)
        request.cachePolicy = .reloadIgnoringLocalCacheData
        let (data, response) = try await session.data(for: request)
        if let http = response as? HTTPURLResponse, !(200..<300).contains(http.statusCode) {
            throw URLError(.badServerResponse)
        }
        return try Self.parse(data, fetchedAt: .now)
    }

    /// Les taux passent par leur écriture décimale la plus courte : 0.9279 reste 0.9279 (et pas 0.92790000000000002).
    static func parse(_ data: Data, fetchedAt: Date) throws -> ExchangeRates {
        let payload = try JSONDecoder().decode(Payload.self, from: data)
        var rates: [String: Decimal] = [:]
        for (code, value) in payload.rates {
            if let rate = Decimal(string: String(value), locale: Locale(identifier: "en_US_POSIX")) {
                rates[code.uppercased()] = rate
            }
        }
        return ExchangeRates(base: payload.base.uppercased(), date: payload.date, rates: rates, fetchedAt: fetchedAt)
    }
}

protocol ExchangeRateCache: Sendable {
    func load() -> ExchangeRates?
    func save(_ rates: ExchangeRates)
}

/// Dernier taux connu, conservé dans les préférences de l'app.
struct UserDefaultsRateCache: ExchangeRateCache, @unchecked Sendable {
    var defaults: UserDefaults = .standard
    var key = "exchangeRates.v1"

    func load() -> ExchangeRates? {
        guard let data = defaults.data(forKey: key) else { return nil }
        return try? JSONDecoder().decode(ExchangeRates.self, from: data)
    }

    func save(_ rates: ExchangeRates) {
        if let data = try? JSONEncoder().encode(rates) { defaults.set(data, forKey: key) }
    }
}

// MARK: - Service

/// Taux courants pour toute l'app. Lit le cache au démarrage, puis rafraîchit au plus une fois
/// toutes les 6 heures ; en cas d'échec réseau, garde le dernier taux connu.
@MainActor
@Observable
final class CurrencyService {
    static let shared = CurrencyService()

    private(set) var rates: ExchangeRates?

    @ObservationIgnored private let source: ExchangeRateSource
    @ObservationIgnored private let cache: ExchangeRateCache
    @ObservationIgnored private let now: () -> Date
    @ObservationIgnored private var refreshTask: Task<Void, Never>?

    static let maxAge: TimeInterval = 6 * 60 * 60

    init(source: ExchangeRateSource = FrankfurterRateSource(),
         cache: ExchangeRateCache = UserDefaultsRateCache(),
         now: @escaping () -> Date = Date.init) {
        self.source = source
        self.cache = cache
        self.now = now
        self.rates = cache.load()
    }

    var isStale: Bool {
        guard let rates else { return true }
        return now().timeIntervalSince(rates.fetchedAt) > Self.maxAge
    }

    func refreshIfNeeded() async {
        guard isStale else { return }
        await refresh()
    }

    /// Récupère les taux du jour. Les appels simultanés partagent la même requête.
    func refresh() async {
        if let refreshTask { return await refreshTask.value }
        let task = Task { [source, cache] in
            do {
                var fresh = try await source.latest()
                fresh.fetchedAt = now()
                rates = fresh
                cache.save(fresh)
            } catch {
                // Hors ligne ou service indisponible : on garde le dernier taux connu.
            }
        }
        refreshTask = task
        await task.value
        refreshTask = nil
    }

    /// Montant converti dans `target`, ou `nil` s'il n'y a rien à convertir
    /// (pas de prix, même devise, devise cible inconnue, taux absent).
    func convert(_ amount: Decimal?, from: String?, to target: String?) -> Decimal? {
        guard let amount, let target, let rates else { return nil }
        let origin = from ?? "EUR"
        guard origin.uppercased() != target.uppercased() else { return nil }
        return rates.convert(amount, from: origin, to: target)
    }

    /// « ≈ 147 € » à côté d'un prix dans une autre devise que celle du profil.
    func approxText(_ amount: Decimal?, from: String?, to target: String?) -> String? {
        guard let target, let value = convert(amount, from: from, to: target) else { return nil }
        return IndicativePrice.text(value, currency: target)
    }

    func spokenText(_ amount: Decimal?, from: String?, to target: String?) -> String? {
        guard let target, let value = convert(amount, from: from, to: target) else { return nil }
        return IndicativePrice.spokenText(value, currency: target)
    }

    /// Total converti de plusieurs devises. `nil` si tout est déjà dans la devise cible,
    /// ou si un taux manque (un total partiel serait trompeur).
    func convertedTotal(_ totals: [(currency: String, amount: Decimal)], to target: String?) -> Decimal? {
        guard let target, let rates, !totals.isEmpty else { return nil }
        guard totals.contains(where: { $0.currency.uppercased() != target.uppercased() }) else { return nil }
        var sum: Decimal = 0
        for total in totals {
            guard let value = rates.convert(total.amount, from: total.currency, to: target) else { return nil }
            sum += value
        }
        return sum
    }
}

extension ItemLink {
    /// « CHF 199.– ≈ 214 € » quand la devise diffère de celle du profil, sinon le prix seul.
    @MainActor
    func priceWithApprox(profileCurrency: String?, service: CurrencyService = .shared) -> String? {
        guard let priceText else { return nil }
        guard let approx = service.approxText(price, from: currency, to: profileCurrency) else { return priceText }
        return "\(priceText) \(approx)"
    }
}
