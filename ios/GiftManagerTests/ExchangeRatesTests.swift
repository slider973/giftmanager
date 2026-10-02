import XCTest
@testable import GiftManager

private struct StubSource: ExchangeRateSource {
    var result: Result<ExchangeRates, Error>
    func latest() async throws -> ExchangeRates { try result.get() }
}

private final class MemoryCache: ExchangeRateCache, @unchecked Sendable {
    var stored: ExchangeRates?
    init(_ stored: ExchangeRates? = nil) { self.stored = stored }
    func load() -> ExchangeRates? { stored }
    func save(_ rates: ExchangeRates) { stored = rates }
}

/// 1 EUR = 0,93 CHF = 0,85 GBP (base BCE).
private func sampleRates(date: String = "2026-10-01", fetchedAt: Date = .now) -> ExchangeRates {
    ExchangeRates(base: "EUR", date: date,
                  rates: ["CHF": Decimal(string: "0.93")!, "GBP": Decimal(string: "0.85")!],
                  fetchedAt: fetchedAt)
}

/// Les formats français utilisent des espaces insécables : on les normalise pour comparer.
private func plain(_ text: String?) -> String? {
    text?.replacingOccurrences(of: "\u{00A0}", with: " ").replacingOccurrences(of: "\u{202F}", with: " ")
}

final class ExchangeRatesTests: XCTestCase {
    // MARK: Conversion

    func testConvertsThroughBaseCurrency() {
        let rates = sampleRates()
        XCTAssertEqual(rates.convert(100, from: "EUR", to: "CHF"), Decimal(93))
        XCTAssertEqual(rates.convert(93, from: "CHF", to: "EUR"), Decimal(100))
        // CHF → GBP : 93 CHF = 100 EUR = 85 GBP.
        XCTAssertEqual(rates.convert(93, from: "chf", to: "GBP"), Decimal(85))
    }

    func testSameCurrencyIsUnchanged() {
        XCTAssertEqual(sampleRates().convert(Decimal(string: "199.99")!, from: "CHF", to: "CHF"), Decimal(string: "199.99"))
    }

    func testMissingRateGivesNoConversion() {
        XCTAssertNil(sampleRates().convert(100, from: "EUR", to: "JPY"))
        XCTAssertNil(sampleRates().convert(100, from: "XYZ", to: "EUR"))
    }

    // MARK: Arrondis et texte

    func testRoundingToUnitsAboveTenAndCentsBelow() {
        XCTAssertEqual(IndicativePrice.rounded(Decimal(string: "147.49")!), 147)
        XCTAssertEqual(IndicativePrice.rounded(Decimal(string: "147.5")!), 148)
        XCTAssertEqual(IndicativePrice.rounded(Decimal(string: "4.567")!), Decimal(string: "4.57"))
    }

    func testIndicativeTextIsMarkedApproximate() {
        XCTAssertEqual(plain(IndicativePrice.text(Decimal(string: "147.2")!, currency: "EUR")), "≈ 147 €")
        XCTAssertEqual(plain(IndicativePrice.text(Decimal(string: "214.5")!, currency: "CHF")), "≈ CHF 215")
        XCTAssertEqual(plain(IndicativePrice.text(Decimal(string: "4.8")!, currency: "EUR")), "≈ 4,80 €")
        XCTAssertEqual(plain(IndicativePrice.spokenText(147, currency: "EUR")), "environ 147 €, conversion indicative")
    }

    // MARK: Service

    @MainActor
    func testServiceHidesConversionForSameCurrencyOrUnknownProfile() {
        let service = CurrencyService(source: StubSource(result: .success(sampleRates())), cache: MemoryCache(sampleRates()))
        XCTAssertNil(service.approxText(199, from: "CHF", to: "CHF"))
        XCTAssertNil(service.approxText(199, from: "CHF", to: nil))
        XCTAssertNil(service.approxText(nil, from: "CHF", to: "EUR"))
        XCTAssertNil(service.approxText(199, from: "CHF", to: "JPY"))
        // 199 CHF / 0,93 = 213,98 € → ≈ 214 €.
        XCTAssertEqual(plain(service.approxText(199, from: "CHF", to: "EUR")), "≈ 214 €")
        // Devise absente d'un lien : EUR, comme pour l'affichage du prix.
        XCTAssertEqual(plain(service.approxText(100, from: nil, to: "CHF")), "≈ CHF 93")
    }

    @MainActor
    func testNoRatesNoConversion() {
        let service = CurrencyService(source: StubSource(result: .failure(URLError(.notConnectedToInternet))), cache: MemoryCache())
        XCTAssertNil(service.approxText(199, from: "CHF", to: "EUR"))
        XCTAssertTrue(service.isStale)
    }

    @MainActor
    func testRefreshStoresRatesInCache() async {
        let cache = MemoryCache()
        let service = CurrencyService(source: StubSource(result: .success(sampleRates(date: "2026-10-02"))), cache: cache)
        await service.refresh()
        XCTAssertEqual(service.rates?.date, "2026-10-02")
        XCTAssertEqual(cache.stored?.date, "2026-10-02")
        XCTAssertFalse(service.isStale)
    }

    @MainActor
    func testOfflineKeepsLastKnownRates() async {
        let suite = "ExchangeRatesTests.\(UUID().uuidString)"
        let defaults = UserDefaults(suiteName: suite)!
        defer { defaults.removePersistentDomain(forName: suite) }
        let cache = UserDefaultsRateCache(defaults: defaults)

        // Premier lancement en ligne : taux du jour mis en cache.
        let online = CurrencyService(source: StubSource(result: .success(sampleRates(date: "2026-09-30"))), cache: cache)
        await online.refresh()

        // Lancement suivant, hors ligne et taux périmés : le dernier taux connu sert encore.
        let later = { Date.now.addingTimeInterval(3 * 24 * 3600) }
        let offline = CurrencyService(source: StubSource(result: .failure(URLError(.notConnectedToInternet))),
                                      cache: cache, now: later)
        XCTAssertTrue(offline.isStale)
        await offline.refreshIfNeeded()
        XCTAssertEqual(offline.rates?.date, "2026-09-30")
        XCTAssertEqual(plain(offline.approxText(93, from: "CHF", to: "EUR")), "≈ 100 €")
    }

    @MainActor
    func testFreshRatesAreNotRefetched() async {
        let cache = MemoryCache(sampleRates(date: "2026-10-01", fetchedAt: .now))
        let service = CurrencyService(source: StubSource(result: .success(sampleRates(date: "2099-01-01"))), cache: cache)
        await service.refreshIfNeeded()
        XCTAssertEqual(service.rates?.date, "2026-10-01")
    }

    @MainActor
    func testConvertedTotal() {
        let service = CurrencyService(source: StubSource(result: .failure(URLError(.badURL))), cache: MemoryCache(sampleRates()))
        // 93 CHF + 50 € = 100 € + 50 € = 150 €.
        XCTAssertEqual(service.convertedTotal([("CHF", 93), ("EUR", 50)], to: "EUR"), Decimal(150))
        // Tout est déjà en euros : rien à convertir.
        XCTAssertNil(service.convertedTotal([("EUR", 50)], to: "EUR"))
        // Un taux manque : pas de total partiel.
        XCTAssertNil(service.convertedTotal([("CHF", 93), ("JPY", 1000)], to: "EUR"))
        XCTAssertNil(service.convertedTotal([], to: "EUR"))
    }

    // MARK: Source

    func testFrankfurterPayloadParsing() throws {
        let json = #"{"amount":1.0,"base":"EUR","date":"2026-10-02","rates":{"CHF":0.9279,"GBP":0.85033}}"#
        let rates = try FrankfurterRateSource.parse(Data(json.utf8), fetchedAt: .now)
        XCTAssertEqual(rates.base, "EUR")
        XCTAssertEqual(rates.date, "2026-10-02")
        XCTAssertEqual(rates.rates["CHF"], Decimal(string: "0.9279"))
        XCTAssertEqual(rates.rates["GBP"], Decimal(string: "0.85033"))
        XCTAssertEqual(rates.publicationText, "2 octobre")
    }
}
