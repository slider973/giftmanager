import Charts
import SwiftUI

/// « Suivi du prix » sur la fiche d'un cadeau que j'ai réservé (#39).
/// Le serveur ne renvoie l'historique qu'à la personne qui a réservé : pour tout autre
/// utilisateur (parents compris), la liste est vide et la section n'apparaît pas.
struct PriceHistorySection: View {
    /// À n'insérer que si j'ai réservé ce cadeau : inutile d'interroger le serveur sinon.
    let itemId: UUID

    @Environment(AppState.self) private var appState
    @State private var trends: [PriceTrend] = []

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            if !trends.isEmpty {
                VStack(alignment: .leading, spacing: Spacing.s) {
                    Text("Suivi du prix")
                        .font(Font.Theme.headline)
                        .foregroundStyle(Color.Theme.textPrimary)
                        .accessibilityAddTraits(.isHeader)
                    ForEach(trends) { trend in
                        PriceTrendRow(trend: trend)
                    }
                    Label("Relevé chaque jour. Tu es prévenu si le prix baisse ou si le cadeau n'est plus en stock.",
                          systemImage: "bell")
                        .font(Font.Theme.caption)
                        .foregroundStyle(Color.Theme.textSecondary)
                        .fixedSize(horizontal: false, vertical: true)
                }
            }
        }
        .task(id: itemId) {
            // Information d'appoint : en cas d'échec, la section reste simplement absente.
            if let checks = try? await appState.repository.priceHistory(itemId: itemId) {
                trends = PriceTrend.trends(from: checks)
            }
        }
    }
}

/// Une boutique : dernier prix, tendance depuis le premier relevé, courbe, stock.
struct PriceTrendRow: View {
    let trend: PriceTrend

    private static let dayFormatter: DateFormatter = {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "fr_FR")
        formatter.dateFormat = "d MMM"
        return formatter
    }()

    private var sinceText: String? {
        trend.first.map { Self.dayFormatter.string(from: $0.date) }
    }

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.s) {
            HStack(alignment: .firstTextBaseline, spacing: Spacing.s) {
                Text(trend.storeName)
                    .font(Font.Theme.callout)
                    .foregroundStyle(Color.Theme.textPrimary)
                    .frame(maxWidth: .infinity, alignment: .leading)
                if let latest = trend.latest, let text = Money.format(latest, currency: trend.currency) {
                    Text(text)
                        .font(Font.Theme.callout.weight(.semibold))
                        .monospacedDigit()
                        .foregroundStyle(Color.Theme.textPrimary)
                }
            }

            if trend.points.count > 1 {
                chart
            }

            HStack(spacing: Spacing.s) {
                changeLabel
                    .frame(maxWidth: .infinity, alignment: .leading)
                if trend.inStock == false {
                    Label("Plus en stock", systemImage: "shippingbox")
                        .font(Font.Theme.captionBold)
                        .foregroundStyle(Color.Theme.takenFg)
                        .padding(.horizontal, Spacing.s)
                        .padding(.vertical, Spacing.xs)
                        .background(Color.Theme.takenBg, in: Capsule())
                }
            }
        }
        .padding(Spacing.m)
        .background(Color.Theme.surface, in: RoundedRectangle(cornerRadius: Radius.field, style: .continuous))
        .overlay {
            RoundedRectangle(cornerRadius: Radius.field, style: .continuous)
                .strokeBorder(Color.Theme.separator, lineWidth: 1)
        }
        .accessibilityElement(children: .combine)
    }

    private var chart: some View {
        let prices = trend.points.map { NSDecimalNumber(decimal: $0.price).doubleValue }
        let low = prices.min() ?? 0
        let high = prices.max() ?? 0
        let pad = max((high - low) * 0.2, high * 0.02, 1)
        return Chart(Array(trend.points.enumerated()), id: \.offset) { _, point in
            LineMark(x: .value("Date", point.date), y: .value("Prix", NSDecimalNumber(decimal: point.price).doubleValue))
                .interpolationMethod(.stepEnd)
                .foregroundStyle(Color.Theme.primary)
                .lineStyle(StrokeStyle(lineWidth: 2, lineCap: .round))
            if point == trend.points.last {
                PointMark(x: .value("Date", point.date), y: .value("Prix", NSDecimalNumber(decimal: point.price).doubleValue))
                    .foregroundStyle(trend.direction == .down ? Color.Theme.availableFg : Color.Theme.primary)
                    .symbolSize(40)
            }
        }
        .chartYScale(domain: (low - pad)...(high + pad))
        .chartXAxis(.hidden)
        .chartYAxis(.hidden)
        .frame(height: 48)
        .accessibilityHidden(true)
    }

    @ViewBuilder
    private var changeLabel: some View {
        let amount = Money.format(trend.change.magnitude, currency: trend.currency) ?? ""
        let since = sinceText.map { " depuis le \($0)" } ?? ""
        switch trend.direction {
        case .down:
            Label("\(amount) de moins\(since)", systemImage: "arrow.down.right")
                .font(Font.Theme.captionBold)
                .foregroundStyle(Color.Theme.availableFg)
        case .up:
            Label("\(amount) de plus\(since)", systemImage: "arrow.up.right")
                .font(Font.Theme.caption)
                .foregroundStyle(Color.Theme.textSecondary)
        case .flat:
            Label(trend.points.isEmpty ? "Prix non relevé" : "Prix stable\(since)", systemImage: "equal")
                .font(Font.Theme.caption)
                .foregroundStyle(Color.Theme.textSecondary)
        }
    }
}

#if DEBUG
#Preview("Tendances") {
    let link = UUID()
    let day: TimeInterval = 86_400
    let trend = PriceTrend(linkId: link, url: "https://www.galaxus.ch/x",
                           points: [.init(date: .now.addingTimeInterval(-6 * day), price: 199),
                                    .init(date: .now.addingTimeInterval(-3 * day), price: 189),
                                    .init(date: .now, price: 169)],
                           currency: "CHF", inStock: true, lastChecked: .now)
    let gone = PriceTrend(linkId: UUID(), url: "https://www.amazon.fr/x",
                          points: [.init(date: .now.addingTimeInterval(-6 * day), price: 199.99)],
                          currency: "EUR", inStock: false, lastChecked: .now)
    return VStack(spacing: Spacing.s) {
        PriceTrendRow(trend: trend)
        PriceTrendRow(trend: gone)
    }
    .padding(Spacing.xl)
    .fcScreenBackground()
}
#endif
