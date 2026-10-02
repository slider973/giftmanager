import SwiftUI

/// Prix converti indicatif, posé **à côté** du prix d'origine (« CHF 199.– ≈ 214 € »).
///
/// Toujours en `textSecondary` : il ne doit jamais prendre le pas sur le prix réel.
/// VoiceOver lit « environ 214 €, conversion indicative ». Hérite de la police de l'appelant.
struct ApproxPriceText: View {
    /// Texte déjà formaté, « ≈ 214 € ».
    let text: String

    var body: some View {
        Text(text)
            .monospacedDigit()
            .foregroundStyle(Color.Theme.textSecondary)
            .accessibilityLabel(Self.spoken(text))
    }

    static func spoken(_ text: String) -> String {
        let amount = text.replacingOccurrences(of: "≈", with: "").trimmingCharacters(in: .whitespaces)
        return "environ \(amount), conversion indicative"
    }
}

/// Mention sous une liste de prix convertis : « ≈ Conversion indicative · taux BCE du 2 octobre ».
struct IndicativeRateNote: View {
    let rateDateText: String?

    var body: some View {
        Label {
            Text(rateDateText.map { "Conversion indicative, taux BCE du \($0). Le prix de la boutique fait foi." }
                 ?? "Conversion indicative. Le prix de la boutique fait foi.")
        } icon: {
            Text("≈").fontWeight(.semibold)
        }
        .font(Font.Theme.caption)
        .foregroundStyle(Color.Theme.textSecondary)
        .fixedSize(horizontal: false, vertical: true)
    }
}

#Preview("Clair") {
    VStack(alignment: .leading, spacing: Spacing.m) {
        HStack(spacing: Spacing.xs) {
            Text("CHF 199.–").monospacedDigit()
            ApproxPriceText(text: "≈ 214 €")
        }
        .font(Font.Theme.caption)
        IndicativeRateNote(rateDateText: "2 octobre")
    }
    .padding(Spacing.xl)
    .fcScreenBackground()
}

#Preview("Sombre") {
    IndicativeRateNote(rateDateText: nil)
        .padding(Spacing.xl)
        .fcScreenBackground()
        .preferredColorScheme(.dark)
}
