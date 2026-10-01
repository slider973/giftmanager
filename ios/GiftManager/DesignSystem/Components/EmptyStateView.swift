import SwiftUI

/// État vide illustré par une mascotte loutre (`Illustrations.xcassets`).
///
/// Ex. `EmptyStateView(imageName: "empty_box", title: "Aucun cadeau pour l'instant",
/// message: "…", actionTitle: "Ajouter un cadeau") { … }`.
struct EmptyStateView: View {
    let imageName: String
    let title: String
    let message: String
    var actionTitle: String? = nil
    var action: (() -> Void)? = nil

    @ScaledMetric(relativeTo: .title2) private var illustrationSize: CGFloat = 180

    var body: some View {
        VStack(spacing: Spacing.l) {
            Image(imageName)
                .resizable()
                .scaledToFit()
                .frame(maxWidth: min(illustrationSize, 260), maxHeight: min(illustrationSize, 260))
                .accessibilityHidden(true)

            VStack(spacing: Spacing.s) {
                Text(title)
                    .font(Font.Theme.title)
                    .foregroundStyle(Color.Theme.textPrimary)
                    .accessibilityAddTraits(.isHeader)

                Text(message)
                    .font(Font.Theme.body)
                    .foregroundStyle(Color.Theme.textSecondary)
                    .lineSpacing(2)
            }
            .multilineTextAlignment(.center)
            .frame(maxWidth: 320)

            if let actionTitle, let action {
                PrimaryButton(title: actionTitle, action: action)
                    .frame(maxWidth: 320)
                    .padding(.top, Spacing.s)
            }
        }
        .padding(.horizontal, Spacing.xl)
        .padding(.vertical, Spacing.xxl)
        .frame(maxWidth: .infinity)
    }
}

#Preview("Clair") {
    EmptyStateView(imageName: "empty_box", title: "Aucun cadeau pour l'instant",
                   message: "Ajoutez une première envie depuis n'importe quelle boutique.",
                   actionTitle: "Ajouter un cadeau") {}
        .fcScreenBackground()
}

#Preview("Sombre") {
    EmptyStateView(imageName: "mascot_sleeping", title: "Tout est calme",
                   message: "Aucune activité récente dans la famille.")
        .fcScreenBackground()
        .preferredColorScheme(.dark)
}
