import SwiftUI

/// Ligne de liste d'un cadeau (écran 3 de la maquette).
///
/// Vignette à gauche ; titre, drapeau + prix, boutique ; cœur en haut à droite ;
/// badge de statut en bas à droite. `status == nil` → **aucun badge** : c'est le
/// mode surprise, quand un parent consulte la liste de son propre enfant.
///
/// Le cœur est un indicateur : la carte entière est la cible tactile, à envelopper
/// dans un `NavigationLink` ou un `Button` par l'écran appelant.
struct GiftCard: View {
    let title: String
    let imageURL: URL?
    let priceText: String?
    let storeText: String?
    let countryCode: String?
    let isFavorite: Bool
    let status: GiftStatus?
    /// Conversion indicative dans la devise du profil (« ≈ 214 € »), affichée après le prix d'origine.
    var approxPriceText: String? = nil

    @Environment(\.dynamicTypeSize) private var dynamicTypeSize
    @ScaledMetric(relativeTo: .headline) private var thumbSize: CGFloat = 76

    var body: some View {
        Group {
            if dynamicTypeSize.isAccessibilitySize {
                VStack(alignment: .leading, spacing: Spacing.m) {
                    HStack(alignment: .top) {
                        thumbnail(side: 96)
                        Spacer(minLength: Spacing.s)
                        heart
                    }
                    details
                    if let status { StatusBadge(status: status) }
                }
            } else {
                HStack(alignment: .top, spacing: Spacing.m) {
                    thumbnail(side: thumbSize)
                    details
                        .frame(maxWidth: .infinity, alignment: .leading)
                    VStack(alignment: .trailing, spacing: Spacing.s) {
                        heart
                        Spacer(minLength: 0)
                        if let status { StatusBadge(status: status) }
                    }
                }
                .fixedSize(horizontal: false, vertical: true)
            }
        }
        .fcCard(padding: Spacing.m)
        .accessibilityElement(children: .combine)
    }

    private func thumbnail(side: CGFloat) -> some View {
        RemoteImage(url: imageURL, placeholderSeed: title)
            .frame(width: side, height: side)
            .clipShape(RoundedRectangle(cornerRadius: Radius.thumb, style: .continuous))
            .accessibilityHidden(true)
    }

    private var heart: some View {
        PriorityHeart(isOn: isFavorite)
            .padding(.top, -Spacing.s)
            .padding(.trailing, -Spacing.s)
    }

    private var details: some View {
        VStack(alignment: .leading, spacing: Spacing.xs) {
            Text(title)
                .font(Font.Theme.headline)
                .foregroundStyle(Color.Theme.textPrimary)
                .lineLimit(dynamicTypeSize.isAccessibilitySize ? nil : 2)
                .padding(.bottom, Spacing.xs / 2)

            if priceText != nil || countryCode != nil {
                HStack(spacing: Spacing.xs + 2) {
                    if let countryCode { CountryFlag(code: countryCode) }
                    if let priceText {
                        Text(priceText)
                            .monospacedDigit()
                            .foregroundStyle(Color.Theme.textPrimary)
                    }
                    if let approxPriceText { ApproxPriceText(text: approxPriceText) }
                }
                .font(Font.Theme.caption)
            }

            if let storeText {
                Label {
                    Text(storeText)
                } icon: {
                    Image(systemName: "bag")
                        .accessibilityHidden(true)
                }
                .labelStyle(CompactLabelStyle())
                .font(Font.Theme.caption)
                .foregroundStyle(Color.Theme.textSecondary)
                .lineLimit(1)
            }
        }
    }
}

/// Icône et texte serrés, alignés sur la ligne de base.
private struct CompactLabelStyle: LabelStyle {
    func makeBody(configuration: Configuration) -> some View {
        HStack(alignment: .firstTextBaseline, spacing: Spacing.xs) {
            configuration.icon
            configuration.title
        }
    }
}

// MARK: - Previews

private struct GiftCardPreviewList: View {
    var body: some View {
        ScrollView {
            VStack(spacing: Spacing.m) {
                GiftCard(title: "LEGO Technic McLaren F1", imageURL: nil, priceText: "CHF 199.–",
                         storeText: "Galaxus", countryCode: "CH", isFavorite: true, status: .available)
                GiftCard(title: "PlayStation 5 Pro", imageURL: nil, priceText: "CHF 799.–",
                         storeText: "Digitec", countryCode: "CH", isFavorite: false, status: .taken)
                GiftCard(title: "Casque Sony WH-1000XM5", imageURL: nil, priceText: "€ 299,00",
                         storeText: "Amazon.fr", countryCode: "FR", isFavorite: true, status: .mine,
                         approxPriceText: "≈ 277 CHF")
                GiftCard(title: "Nintendo Switch OLED", imageURL: URL(string: "https://invalid.invalid/x.png"),
                         priceText: "CHF 349.–", storeText: "Fnac", countryCode: "CH", isFavorite: false,
                         status: .owned)
                // Mode surprise (vue parent) : aucun badge.
                GiftCard(title: "Maillot PSG 2025 avec un nom très long qui passe sur deux lignes",
                         imageURL: nil, priceText: nil, storeText: nil, countryCode: nil,
                         isFavorite: true, status: nil)
            }
            .padding(Spacing.xl)
        }
        .fcScreenBackground()
    }
}

#Preview("Clair") {
    GiftCardPreviewList()
}

#Preview("Sombre") {
    GiftCardPreviewList().preferredColorScheme(.dark)
}

#Preview("Texte XXXL") {
    GiftCardPreviewList().dynamicTypeSize(.accessibility2)
}
