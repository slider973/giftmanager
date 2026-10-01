import SwiftUI

/// En-tête de section (« Événements à venir ») avec bouton rond optionnel.
struct SectionHeader: View {
    let title: String
    var actionSystemImage: String? = nil
    var action: (() -> Void)? = nil
    /// Libellé VoiceOver du bouton ; « Ajouter » par défaut.
    var actionLabel: String? = nil

    @ScaledMetric(relativeTo: .headline) private var badgeSize: CGFloat = 32

    var body: some View {
        HStack(alignment: .center, spacing: Spacing.s) {
            Text(title)
                .font(Font.Theme.headline)
                .foregroundStyle(Color.Theme.textPrimary)
                .accessibilityAddTraits(.isHeader)
                .frame(maxWidth: .infinity, alignment: .leading)

            if let actionSystemImage, let action {
                Button(action: action) {
                    Image(systemName: actionSystemImage)
                        .font(.system(size: badgeSize * 0.45, weight: .bold))
                        .foregroundStyle(Color.Theme.onPrimary)
                        .frame(width: badgeSize, height: badgeSize)
                        .background(Color.Theme.primary, in: Circle())
                        .frame(minWidth: HitTarget.minimum, minHeight: HitTarget.minimum)
                        .contentShape(Rectangle())
                }
                .buttonStyle(FCPressableStyle(pressedScale: 0.9))
                .padding(.trailing, -Spacing.s)
                .accessibilityLabel(actionLabel ?? "Ajouter")
            }
        }
        .frame(minHeight: HitTarget.minimum)
    }
}

private struct SectionHeaderPreview: View {
    var body: some View {
        VStack(spacing: Spacing.l) {
            SectionHeader(title: "Événements à venir", actionSystemImage: "plus", action: {},
                          actionLabel: "Ajouter un événement")
            SectionHeader(title: "Événements passés")
        }
        .padding(Spacing.xl)
        .fcScreenBackground()
    }
}

#Preview("Clair") {
    SectionHeaderPreview()
}

#Preview("Sombre") {
    SectionHeaderPreview().preferredColorScheme(.dark)
}
