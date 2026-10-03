import SwiftUI

/// Progression d'une cagnotte (#35) : montant réuni, jauge vers le prix, participants.
///
/// Jamais affichée à un parent (le serveur ne renvoie aucune donnée de cagnotte pour lui).
/// Sans prix de référence dans la devise de la cagnotte, la jauge disparaît : on montre
/// seulement le montant réuni, plutôt qu'une barre inventée.
struct PotProgressCard: View {
    let progress: PotProgress
    /// Ma part, si je participe.
    var myShareText: String? = nil

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var shownFraction: Double = 0

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.m) {
            HStack(spacing: Spacing.s) {
                Image(systemName: "person.3.fill")
                    .font(Font.Theme.caption.weight(.semibold))
                    .foregroundStyle(Color.Theme.potFg)
                    .frame(width: 30, height: 30)
                    .background(Color.Theme.potBg, in: Circle())
                    .accessibilityHidden(true)
                Text("Cagnotte")
                    .font(Font.Theme.headline)
                    .foregroundStyle(Color.Theme.textPrimary)
                Spacer(minLength: Spacing.s)
                if progress.isComplete {
                    Label("Objectif atteint", systemImage: "checkmark.circle.fill")
                        .font(Font.Theme.captionBold)
                        .foregroundStyle(Color.Theme.availableFg)
                        .padding(.horizontal, Spacing.s + 2)
                        .padding(.vertical, Spacing.xs)
                        .background(Color.Theme.availableBg, in: Capsule())
                        .fixedSize()
                }
            }

            amountLine

            if let fraction = progress.fraction {
                GeometryReader { proxy in
                    ZStack(alignment: .leading) {
                        Capsule().fill(Color.Theme.potBg)
                        Capsule()
                            .fill(Color.Theme.potFg)
                            // Toujours un bout de jauge visible dès qu'il y a une participation.
                            .frame(width: max(proxy.size.width * shownFraction, shownFraction > 0 ? 10 : 0))
                    }
                }
                .frame(height: 10)
                .accessibilityHidden(true)
                .onAppear { animate(to: fraction) }
                .onChange(of: fraction) { _, value in animate(to: value) }
            }

            Text(detailLine)
                .font(Font.Theme.caption)
                .monospacedDigit()
                .foregroundStyle(Color.Theme.textSecondary)
                .fixedSize(horizontal: false, vertical: true)
        }
        .fcCard()
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(accessibilityText)
    }

    private var amountLine: some View {
        Group {
            if let target = progress.targetText {
                Text(progress.collectedText).font(Font.Theme.title).foregroundStyle(Color.Theme.textPrimary)
                    + Text("  sur \(target)").font(Font.Theme.callout).foregroundStyle(Color.Theme.textSecondary)
            } else {
                Text(progress.collectedText).font(Font.Theme.title).foregroundStyle(Color.Theme.textPrimary)
                    + Text("  réunis").font(Font.Theme.callout).foregroundStyle(Color.Theme.textSecondary)
            }
        }
        .monospacedDigit()
        .fixedSize(horizontal: false, vertical: true)
    }

    private var detailLine: String {
        var parts = [progress.participantsText]
        if let myShareText { parts.append("ma part : \(myShareText)") }
        if let remaining = progress.remainingText, !progress.isComplete { parts.append("encore \(remaining)") }
        return parts.joined(separator: " · ")
    }

    private var accessibilityText: String {
        var text = "Cagnotte : \(progress.collectedText) réunis"
        if let target = progress.targetText { text += " sur \(target)" }
        text += ", \(progress.participantsText)"
        if let myShareText { text += ", ma part \(myShareText)" }
        if progress.isComplete {
            text += ", objectif atteint"
        } else if let remaining = progress.remainingText {
            text += ", il manque \(remaining)"
        }
        return text
    }

    private func animate(to value: Double) {
        if reduceMotion {
            shownFraction = value
        } else {
            withAnimation(.spring(response: 0.6, dampingFraction: 0.85)) { shownFraction = value }
        }
    }
}

private struct PotProgressCardPreview: View {
    var body: some View {
        VStack(spacing: Spacing.m) {
            PotProgressCard(progress: PotProgress(collected: 120, currency: "CHF", target: 199, participants: 3),
                            myShareText: "CHF 40.00")
            PotProgressCard(progress: PotProgress(collected: 15, currency: "EUR", target: 299, participants: 1))
            PotProgressCard(progress: PotProgress(collected: 210, currency: "CHF", target: 199, participants: 5))
            PotProgressCard(progress: PotProgress(collected: 60, currency: "EUR", target: nil, participants: 2))
        }
        .padding(Spacing.xl)
        .fcScreenBackground()
    }
}

#Preview("Clair") {
    PotProgressCardPreview()
}

#Preview("Sombre") {
    PotProgressCardPreview().preferredColorScheme(.dark)
}

#Preview("Texte XXXL") {
    ScrollView { PotProgressCardPreview() }.dynamicTypeSize(.accessibility2)
}
