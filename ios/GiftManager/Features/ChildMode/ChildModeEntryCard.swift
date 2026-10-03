import SwiftUI

/// Entrée « Mode enfant » en tête de la liste d'un de mes enfants (#36).
struct ChildModeEntryCard: View {
    let childName: String
    let action: () -> Void

    @ScaledMetric(relativeTo: .headline) private var mascotSize: CGFloat = 52

    var body: some View {
        Button(action: action) {
            HStack(spacing: Spacing.m) {
                Circle()
                    .fill(Color.Theme.pastelPink)
                    .frame(width: mascotSize, height: mascotSize)
                    .overlay {
                        Image("mascot_love")
                            .resizable()
                            .scaledToFit()
                            .padding(Spacing.xs)
                    }
                    .accessibilityHidden(true)
                VStack(alignment: .leading, spacing: 2) {
                    Text("Mode enfant")
                        .font(Font.Theme.headline)
                        .foregroundStyle(Color.Theme.textPrimary)
                    Text("\(childName) choisit ses envies en grand, sans prix ni statut.")
                        .font(Font.Theme.caption)
                        .foregroundStyle(Color.Theme.textSecondary)
                        .fixedSize(horizontal: false, vertical: true)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                Image(systemName: "chevron.right")
                    .font(Font.Theme.captionBold)
                    .foregroundStyle(Color.Theme.textSecondary)
                    .accessibilityHidden(true)
            }
            .fcCard(padding: Spacing.m)
            .contentShape(RoundedRectangle(cornerRadius: Radius.card, style: .continuous))
        }
        .buttonStyle(FCPressableStyle())
        .accessibilityElement(children: .combine)
        .accessibilityHint("Ouvre un affichage simplifié pour que \(childName) pose des cœurs sur ses envies.")
    }
}

#Preview("Clair") {
    ChildModeEntryCard(childName: "Léo") {}
        .padding(Spacing.xl)
        .fcScreenBackground()
}

#Preview("Sombre") {
    ChildModeEntryCard(childName: "Léo") {}
        .padding(Spacing.xl)
        .fcScreenBackground()
        .preferredColorScheme(.dark)
}
