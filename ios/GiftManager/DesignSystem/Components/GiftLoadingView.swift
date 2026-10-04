import SwiftUI

/// Animation de chargement : l'illustration `gift_red` qui flotte doucement.
///
/// Volontairement sobre et continue. Une première version découpait l'image pour faire
/// s'envoler le couvercle, en enchaînant des étapes pilotées par `Task.sleep` : le résultat
/// saccadait (chaque étape redémarrait une animation) et la découpe d'un rendu 3D en deux
/// morceaux plats se voyait. Ici, une seule animation continue, jamais interrompue : c'est
/// ce qui rend le mouvement fluide.
///
/// Le paquet monte et descend, s'incline légèrement, son ombre respire, et trois étincelles
/// scintillent autour. « Réduire les animations » (Réglages iOS) fige le tout.
struct GiftLoadingView: View {
    /// Taille de l'illustration ; tout le reste est proportionnel.
    var size: CGFloat = 120
    /// Texte optionnel sous le cadeau.
    var label: String?

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var animating = false

    var body: some View {
        VStack(spacing: Spacing.xl) {
            ZStack {
                shadow
                sparkles
                gift
            }
            .frame(width: size * 1.6, height: size * 1.6)
            .accessibilityHidden(true)

            if let label {
                Text(label)
                    .font(Font.Theme.caption)
                    .foregroundStyle(Color.Theme.textSecondary)
            }
        }
        .accessibilityElement(children: .combine)
        .accessibilityLabel(label ?? "Chargement en cours")
        .onAppear {
            guard !reduceMotion else { return }
            animating = true
        }
    }

    /// Le cadeau : il flotte et s'incline, d'un seul tenant.
    private var gift: some View {
        Image("gift_red")
            .resizable()
            .interpolation(.high)
            .scaledToFit()
            .frame(width: size, height: size)
            .rotationEffect(.degrees(animating ? 5 : -5))
            .offset(y: animating ? -size * 0.09 : size * 0.04)
            .animation(float, value: animating)
    }

    /// Ombre portée : elle se resserre quand le paquet monte.
    private var shadow: some View {
        Ellipse()
            .fill(Color.Theme.textPrimary.opacity(0.1))
            .frame(width: size * (animating ? 0.42 : 0.6), height: size * 0.1)
            .blur(radius: 6)
            .offset(y: size * 0.56)
            .animation(float, value: animating)
    }

    /// Trois étincelles qui scintillent en décalé, chacune à son rythme.
    private var sparkles: some View {
        ForEach(0..<3, id: \.self) { index in
            Sparkle()
                .fill(Color.Theme.accentAmber)
                .frame(width: size * 0.17, height: size * 0.17)
                .offset(sparkleOffset(index))
                .scaleEffect(animating ? 1 : 0.35)
                .opacity(animating ? 0.95 : 0.15)
                .animation(twinkle(index), value: animating)
        }
    }

    private func sparkleOffset(_ index: Int) -> CGSize {
        switch index {
        case 0: CGSize(width: -size * 0.52, height: -size * 0.3)
        case 1: CGSize(width: size * 0.5, height: -size * 0.42)
        default: CGSize(width: size * 0.36, height: size * 0.3)
        }
    }

    /// Va-et-vient permanent : une seule animation, jamais relancée, donc fluide.
    private var float: Animation {
        .easeInOut(duration: 1.8).repeatForever(autoreverses: true)
    }

    /// Scintillement décalé pour que les trois étincelles ne battent pas ensemble.
    private func twinkle(_ index: Int) -> Animation {
        .easeInOut(duration: 1.1 + Double(index) * 0.35)
        .repeatForever(autoreverses: true)
        .delay(Double(index) * 0.3)
    }
}

/// Étoile à quatre branches, aux pointes incurvées.
private struct Sparkle: Shape {
    func path(in rect: CGRect) -> Path {
        var path = Path()
        let c = CGPoint(x: rect.midX, y: rect.midY)
        let r = min(rect.width, rect.height) / 2
        let waist = r * 0.28

        path.move(to: CGPoint(x: c.x, y: c.y - r))
        path.addQuadCurve(to: CGPoint(x: c.x + r, y: c.y),
                          control: CGPoint(x: c.x + waist, y: c.y - waist))
        path.addQuadCurve(to: CGPoint(x: c.x, y: c.y + r),
                          control: CGPoint(x: c.x + waist, y: c.y + waist))
        path.addQuadCurve(to: CGPoint(x: c.x - r, y: c.y),
                          control: CGPoint(x: c.x - waist, y: c.y + waist))
        path.addQuadCurve(to: CGPoint(x: c.x, y: c.y - r),
                          control: CGPoint(x: c.x - waist, y: c.y - waist))
        path.closeSubpath()
        return path
    }
}

#Preview("Chargement") {
    GiftLoadingView(label: "Chargement…")
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .fcScreenBackground()
}
