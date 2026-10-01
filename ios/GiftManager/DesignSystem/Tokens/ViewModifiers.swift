import SwiftUI

// MARK: - Carte

private struct FCCardModifier: ViewModifier {
    var padding: CGFloat

    func body(content: Content) -> some View {
        content
            .padding(padding)
            .background {
                RoundedRectangle(cornerRadius: Radius.card, style: .continuous)
                    .fill(Color.Theme.surface)
                    .shadow(color: .black.opacity(0.06), radius: 4, x: 0, y: 2)
            }
    }
}

// MARK: - Fond d'écran

private struct FCScreenBackgroundModifier: ViewModifier {
    func body(content: Content) -> some View {
        content
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .background(Color.Theme.background.ignoresSafeArea())
    }
}

// MARK: - Retour tactile des boutons

/// Style de pression partagé : léger enfoncement + opacité, sans décaler la mise en page.
struct FCPressableStyle: ButtonStyle {
    var pressedScale: CGFloat = 0.97
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .scaleEffect(configuration.isPressed && !reduceMotion ? pressedScale : 1)
            .opacity(configuration.isPressed ? 0.85 : 1)
            .animation(.spring(response: 0.25, dampingFraction: 0.8), value: configuration.isPressed)
    }
}

extension View {
    /// Carte blanche très arrondie : fond `surface`, rayon `Radius.card`,
    /// ombre légère (y 2, flou 8, 6 %), padding interne `Spacing.l` par défaut.
    func fcCard(padding: CGFloat = Spacing.l) -> some View {
        modifier(FCCardModifier(padding: padding))
    }

    /// Fond crème plein écran, sous les zones de sécurité.
    func fcScreenBackground() -> some View {
        modifier(FCScreenBackgroundModifier())
    }
}
