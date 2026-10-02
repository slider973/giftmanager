import SwiftUI

/// Ligne « Liens par pays » (écran 5) : drapeau, boutique, prix, lien externe.
/// Toute la ligne est cliquable.
struct StoreLinkRow: View {
    let store: String
    let countryCode: String?
    let priceText: String?
    /// Conversion indicative dans la devise du profil, sous le prix d'origine.
    var approxPriceText: String? = nil
    let action: () -> Void

    @ScaledMetric(relativeTo: .body) private var minHeight: CGFloat = 52

    var body: some View {
        Button(action: action) {
            HStack(spacing: Spacing.m) {
                CountryFlag(code: countryCode ?? "")
                    .font(.title3)
                    .accessibilityHidden(true)

                Text(store)
                    .font(Font.Theme.callout)
                    .foregroundStyle(Color.Theme.textPrimary)
                    .frame(maxWidth: .infinity, alignment: .leading)

                if let priceText {
                    VStack(alignment: .trailing, spacing: 2) {
                        Text(priceText)
                            .font(Font.Theme.callout.weight(.semibold))
                            .monospacedDigit()
                            .foregroundStyle(Color.Theme.textPrimary)
                        if let approxPriceText {
                            ApproxPriceText(text: approxPriceText)
                                .font(Font.Theme.caption)
                        }
                    }
                }

                Image(systemName: "arrow.up.right.square")
                    .font(Font.Theme.callout)
                    .foregroundStyle(Color.Theme.textSecondary)
                    .accessibilityHidden(true)
            }
            .padding(.horizontal, Spacing.l)
            .padding(.vertical, Spacing.s)
            .frame(minHeight: minHeight)
            .background(Color.Theme.surface, in: RoundedRectangle(cornerRadius: Radius.field, style: .continuous))
            .overlay {
                RoundedRectangle(cornerRadius: Radius.field, style: .continuous)
                    .strokeBorder(Color.Theme.separator, lineWidth: 1)
            }
            .contentShape(RoundedRectangle(cornerRadius: Radius.field, style: .continuous))
        }
        .buttonStyle(FCPressableStyle(pressedScale: 0.98))
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(accessibilityText)
        .accessibilityHint("Ouvre le site de la boutique")
        .accessibilityAddTraits(.isLink)
    }

    private var accessibilityText: String {
        var parts = [store]
        if let countryCode, CountryFlag.normalized(countryCode) != nil {
            parts.append(CountryFlag.countryName(for: countryCode))
        }
        if let priceText { parts.append(priceText) }
        if let approxPriceText { parts.append(ApproxPriceText.spoken(approxPriceText)) }
        return parts.joined(separator: ", ")
    }
}

private struct StoreLinkRowPreview: View {
    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.s) {
            SectionHeader(title: "Liens par pays")
            StoreLinkRow(store: "Galaxus (CH)", countryCode: "CH", priceText: "CHF 199.–") {}
            StoreLinkRow(store: "Amazon.fr (FR)", countryCode: "FR", priceText: "€ 199,99", approxPriceText: "≈ 185 CHF") {}
            StoreLinkRow(store: "Fnac (FR)", countryCode: "FR", priceText: "€ 199,99") {}
            StoreLinkRow(store: "Boutique en ligne", countryCode: nil, priceText: nil) {}
        }
        .padding(Spacing.xl)
        .fcScreenBackground()
    }
}

#Preview("Clair") {
    StoreLinkRowPreview()
}

#Preview("Sombre") {
    StoreLinkRowPreview().preferredColorScheme(.dark)
}
