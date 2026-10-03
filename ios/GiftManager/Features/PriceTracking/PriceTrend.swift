import Foundation

/// Évolution du prix d'un lien d'un cadeau réservé (#39), calculée à partir des relevés.
struct PriceTrend: Identifiable, Equatable {
    struct Point: Equatable {
        let date: Date
        let price: Decimal
    }

    enum Direction: Equatable { case down, up, flat }

    let linkId: UUID
    let url: String
    /// Relevés avec un prix, du plus ancien au plus récent (même devise que le dernier relevé).
    let points: [Point]
    let currency: String?
    /// État de stock du dernier relevé qui le précise.
    let inStock: Bool?
    let lastChecked: Date

    var id: UUID { linkId }
    var latest: Decimal? { points.last?.price }
    var first: Point? { points.first }

    /// Écart entre le premier et le dernier prix relevés.
    var change: Decimal {
        guard let first = points.first?.price, let last = points.last?.price else { return 0 }
        return last - first
    }

    var direction: Direction {
        if change < 0 { return .down }
        if change > 0 { return .up }
        return .flat
    }

    var storeName: String { StoreCatalog.store(for: url)?.name ?? "Boutique" }

    /// Regroupe les relevés par lien. Les liens sans aucun relevé exploitable sont ignorés ;
    /// les liens dont le prix a baissé ou qui ne sont plus en stock passent en tête.
    static func trends(from checks: [PriceCheck]) -> [PriceTrend] {
        let byLink = Dictionary(grouping: checks, by: \.linkId)
        let trends: [PriceTrend] = byLink.compactMap { linkId, rows in
            let sorted = rows.sorted { $0.checkedAt < $1.checkedAt }
            guard let last = sorted.last else { return nil }
            let currency = sorted.last(where: { $0.price != nil })?.currency
            let points = sorted.compactMap { row -> Point? in
                guard let price = row.price, row.currency == currency else { return nil }
                return Point(date: row.checkedAt, price: price)
            }
            let inStock = sorted.last(where: { $0.inStock != nil })?.inStock
            guard !points.isEmpty || inStock != nil else { return nil }
            return PriceTrend(linkId: linkId, url: last.url, points: points, currency: currency,
                              inStock: inStock, lastChecked: last.checkedAt)
        }
        return trends.sorted { a, b in
            let ra = a.rank, rb = b.rank
            return ra != rb ? ra < rb : a.lastChecked > b.lastChecked
        }
    }

    private var rank: Int {
        if inStock == false { return 0 }
        return direction == .down ? 1 : 2
    }
}
