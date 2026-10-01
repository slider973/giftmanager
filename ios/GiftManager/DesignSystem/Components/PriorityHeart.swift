import SwiftUI

/// Cœur « très envie ». Plein et rouge quand actif, contour discret sinon.
///
/// - Avec `action` : bouton bascule (cible 44 × 44 pt, retour haptique).
/// - Sans `action` : simple indicateur, même encombrement pour aligner les cartes.
struct PriorityHeart: View {
    let isOn: Bool
    var action: (() -> Void)? = nil

    @ScaledMetric(relativeTo: .body) private var glyphSize: CGFloat = 20

    var body: some View {
        if let action {
            Button(action: action) { glyph }
                .buttonStyle(FCPressableStyle(pressedScale: 0.85))
                .sensoryFeedback(.selection, trigger: isOn)
                .accessibilityLabel("Très envie")
                .accessibilityValue(isOn ? "Activé" : "Désactivé")
                .accessibilityAddTraits(isOn ? .isSelected : [])
                .accessibilityHint(isOn ? "Retire la priorité" : "Marque ce cadeau comme très attendu")
        } else {
            glyph
                .accessibilityElement(children: .ignore)
                .accessibilityLabel(isOn ? "Très envie" : "Envie normale")
        }
    }

    private var glyph: some View {
        Image(systemName: isOn ? "heart.fill" : "heart")
            .font(.system(size: glyphSize, weight: .semibold))
            .foregroundStyle(isOn ? Color.Theme.heart : Color.Theme.textSecondary)
            .contentTransition(.symbolEffect(.replace))
            .frame(minWidth: HitTarget.minimum, minHeight: HitTarget.minimum)
            .contentShape(Rectangle())
    }
}

private struct PriorityHeartDemo: View {
    @State private var isOn = true

    var body: some View {
        HStack(spacing: Spacing.l) {
            PriorityHeart(isOn: isOn) { isOn.toggle() }
            PriorityHeart(isOn: true)
            PriorityHeart(isOn: false)
        }
        .padding(Spacing.l)
        .fcCard()
        .padding(Spacing.xl)
        .fcScreenBackground()
    }
}

#Preview("Clair") {
    PriorityHeartDemo()
}

#Preview("Sombre") {
    PriorityHeartDemo().preferredColorScheme(.dark)
}
