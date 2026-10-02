import SwiftUI

/// Mention « conversion indicative » sous les liens d'une fiche cadeau,
/// seulement si au moins un prix y est converti (#38).
struct IndicativeRateFootnote: View {
    let links: [ItemLink]
    let profileCurrency: String?
    private var service: CurrencyService { .shared }

    private var hasConversion: Bool {
        links.contains { service.convert($0.price, from: $0.currency, to: profileCurrency) != nil }
    }

    var body: some View {
        if hasConversion {
            IndicativeRateNote(rateDateText: service.rates?.publicationText)
                .padding(.top, Spacing.xs)
        }
    }
}

/// Total de « Mes achats » converti dans la devise du profil : « ≈ 412 € au total · indicatif ».
/// Absent si tout est déjà dans cette devise ou si un taux manque.
struct ConvertedTotalLine: View {
    let totals: [(currency: String, amount: Decimal)]
    let profileCurrency: String?
    private var service: CurrencyService { .shared }

    var body: some View {
        if let profileCurrency, let total = service.convertedTotal(totals, to: profileCurrency) {
            let amount = IndicativePrice.amountText(total, currency: profileCurrency)
            Text("≈ \(amount) au total · indicatif")
                .font(Font.Theme.captionBold)
                .monospacedDigit()
                .foregroundStyle(Color.Theme.textSecondary)
                .fixedSize(horizontal: false, vertical: true)
                .accessibilityLabel("environ \(amount) au total, conversion indicative")
        }
    }
}
