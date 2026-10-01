import SwiftUI

/// Pastille de fonctionnalité (en-tête de la maquette, onboarding) :
/// icône colorée sur disque pastel, titre et sous-titre centrés.
///
/// `color` teinte l'icône. Le disque prend `background` si fourni (un pastel du
/// thème, comme sur la maquette), sinon une teinte de `color` à 16 % sur `surface`.
/// Paires conseillées : `heart`/`pastelPink`, `availableFg`/`pastelMint`,
/// `mineFg`/`pastelBlue`, `accentAmber`/`pastelPeach`, `ownedFg`/`pastelLavender`.
struct FeaturePill: View {
    let systemImage: String
    let color: Color
    let title: String
    let subtitle: String
    var background: Color? = nil

    @ScaledMetric(relativeTo: .headline) private var discSize: CGFloat = 64

    var body: some View {
        VStack(spacing: Spacing.s) {
            Image(systemName: systemImage)
                .symbolRenderingMode(.hierarchical)
                .font(.system(size: discSize * 0.42, weight: .semibold))
                .foregroundStyle(color)
                .frame(width: discSize, height: discSize)
                .background {
                    if let background {
                        Circle().fill(background)
                    } else {
                        Circle().fill(Color.Theme.surface).overlay(Circle().fill(color.opacity(0.16)))
                    }
                }
                .accessibilityHidden(true)
                .padding(.bottom, Spacing.xs)

            Text(title)
                .font(Font.Theme.headline)
                .foregroundStyle(Color.Theme.textPrimary)

            Text(subtitle)
                .font(Font.Theme.caption)
                .foregroundStyle(Color.Theme.textSecondary)
        }
        .multilineTextAlignment(.center)
        .fixedSize(horizontal: false, vertical: true)
        .accessibilityElement(children: .combine)
    }
}

private struct FeaturePillPreview: View {
    var body: some View {
        LazyVGrid(columns: [GridItem(.flexible(), alignment: .top), GridItem(.flexible(), alignment: .top)],
                  spacing: Spacing.xl) {
            FeaturePill(systemImage: "person.2.fill", color: Color.Theme.heart,
                        title: "Groupes familiaux", subtitle: "Plusieurs foyers, plusieurs pays",
                        background: Color.Theme.pastelPink)
            FeaturePill(systemImage: "gift.fill", color: Color.Theme.availableFg,
                        title: "Listes par événement", subtitle: "Anniversaire, Noël, et plus",
                        background: Color.Theme.pastelMint)
            FeaturePill(systemImage: "link", color: Color.Theme.mineFg,
                        title: "Toutes les boutiques", subtitle: "Amazon, Galaxus, Fnac, etc.",
                        background: Color.Theme.pastelBlue)
            FeaturePill(systemImage: "lock.fill", color: Color.Theme.accentAmber,
                        title: "Surprise garantie", subtitle: "« Déjà pris » sans savoir par qui",
                        background: Color.Theme.pastelPeach)
            FeaturePill(systemImage: "checkmark.shield.fill", color: Color.Theme.ownedFg,
                        title: "Enfants protégés", subtitle: "Les parents ne voient pas les réservations",
                        background: Color.Theme.pastelLavender)
        }
        .padding(Spacing.xl)
        .fcScreenBackground()
    }
}

#Preview("Clair") {
    FeaturePillPreview()
}

#Preview("Sombre") {
    FeaturePillPreview().preferredColorScheme(.dark)
}
