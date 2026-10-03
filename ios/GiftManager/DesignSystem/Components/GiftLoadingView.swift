import SwiftUI

/// Animation de chargement : l'illustration `gift_red` qui s'ouvre.
///
/// L'illustration d'origine est conservée telle quelle — c'est l'identité visuelle de l'app.
/// Elle est simplement découpée en deux par masque (couvercle au-dessus, boîte en dessous)
/// pour que le couvercle puisse s'envoler, puis enrichie de lumière, d'étincelles et de confettis.
///
/// Séquence : tremblement → le couvercle se soulève et s'envole → lumière → confettis → reprise.
/// « Réduire les animations » (Réglages iOS) remplace le tout par une respiration douce.
struct GiftLoadingView: View {
    /// Taille de l'illustration ; tout le reste est proportionnel.
    var size: CGFloat = 104
    /// Texte optionnel sous le cadeau.
    var label: String?

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var phase = Phase.idle
    @State private var breathing = false

    /// Étapes du cycle, avec leur durée.
    private enum Phase: CaseIterable {
        case idle, shaking, opening, bursting

        var duration: Double {
            switch self {
            case .idle: 0.5
            case .shaking: 1.0
            case .opening: 0.7
            case .bursting: 1.1
            }
        }
    }

    private var isOpen: Bool { phase == .opening || phase == .bursting }

    /// Part haute de l'illustration occupée par le couvercle et le nœud.
    /// Mesurée sur `gift_red.png` : le bord bas du couvercle tombe à 54 % de la hauteur.
    private static let lidFraction: CGFloat = 0.54

    var body: some View {
        VStack(spacing: Spacing.l) {
            ZStack {
                glow
                confetti
                sparkles
                gift
            }
            .frame(width: size * 2, height: size * 2)
            .accessibilityHidden(true)

            if let label {
                Text(label)
                    .font(Font.Theme.caption)
                    .foregroundStyle(Color.Theme.textSecondary)
            }
        }
        .accessibilityElement(children: .combine)
        .accessibilityLabel(label ?? "Chargement en cours")
        .task {
            guard !reduceMotion else {
                withAnimation(.easeInOut(duration: 1.4).repeatForever(autoreverses: true)) {
                    breathing = true
                }
                return
            }
            await runLoop()
        }
    }

    /// Boucle tant que la vue est affichée ; s'arrête d'elle-même à la disparition.
    private func runLoop() async {
        while !Task.isCancelled {
            for next in Phase.allCases {
                guard !Task.isCancelled else { return }
                withAnimation(animation(for: next)) { phase = next }
                try? await Task.sleep(for: .seconds(next.duration))
            }
            // Retour à l'état fermé sans animation, pour que la boucle reparte proprement.
            phase = .idle
        }
    }

    private func animation(for phase: Phase) -> Animation {
        switch phase {
        case .idle: .easeOut(duration: 0.3)
        case .shaking: .easeInOut(duration: 0.09).repeatCount(10, autoreverses: true)
        case .opening: .spring(response: 0.5, dampingFraction: 0.6)
        case .bursting: .easeIn(duration: 1.0)
        }
    }

    // MARK: - Le cadeau

    /// L'illustration, coupée en deux seulement à l'ouverture : le couvercle s'envole,
    /// la boîte reste. Tant que le cadeau est fermé, l'image est rendue d'un seul tenant —
    /// sinon la moindre rotation laisse voir un trait de coupe.
    private var gift: some View {
        ZStack {
            if isOpen {
                // Partie basse : la boîte.
                giftImage
                    .mask(alignment: .bottom) {
                        Rectangle()
                            .frame(height: size * (1 - Self.lidFraction))
                    }
                    // La boîte s'écrase légèrement quand le couvercle part : elle « souffle ».
                    .scaleEffect(x: 1.05, y: 0.95, anchor: .bottom)

                // Partie haute : le couvercle et son nœud.
                giftImage
                    .mask(alignment: .top) {
                        Rectangle()
                            .frame(height: size * Self.lidFraction)
                    }
                    .offset(y: lidOffset)
                    .rotationEffect(.degrees(lidAngle), anchor: .bottom)
                    .opacity(phase == .bursting ? 0.5 : 1)
                    .scaleEffect(phase == .bursting ? 0.88 : 1)
            } else {
                giftImage
            }
        }
        .rotationEffect(.degrees(phase == .shaking ? 3.5 : 0), anchor: .bottom)
        .scaleEffect(reduceMotion && breathing ? 1.04 : 1)
    }

    private var giftImage: some View {
        Image("gift_red")
            .resizable()
            .interpolation(.high)
            .scaledToFit()
            .frame(width: size, height: size)
    }

    /// Hauteur dont le couvercle s'éloigne de la boîte.
    private var lidOffset: CGFloat {
        switch phase {
        case .idle: 0
        case .shaking: 0
        case .opening: -size * 0.42
        case .bursting: -size * 0.78
        }
    }

    private var lidAngle: Double {
        switch phase {
        case .idle: 0
        case .shaking: 0
        case .opening: -12
        case .bursting: -22
        }
    }

    // MARK: - Effets

    /// Lumière qui jaillit de la boîte ouverte.
    private var glow: some View {
        Circle()
            .fill(
                RadialGradient(colors: [Color.Theme.accentAmber.opacity(0.6), .clear],
                               center: .center, startRadius: 0, endRadius: size * 0.85)
            )
            .frame(width: size * 1.7, height: size * 1.7)
            .scaleEffect(isOpen ? 1 : 0.25)
            .opacity(isOpen ? (phase == .bursting ? 0.5 : 1) : 0)
            .offset(y: -size * 0.1)
            .blur(radius: 8)
    }

    private var sparkles: some View {
        ForEach(0..<3, id: \.self) { index in
            Sparkle()
                .fill(Color.Theme.accentAmber)
                .frame(width: size * 0.2, height: size * 0.2)
                .offset(sparkleOffset(index))
                .scaleEffect(isOpen ? 1 : 0.1)
                .opacity(isOpen ? (phase == .bursting ? 0.4 : 1) : 0)
        }
    }

    private func sparkleOffset(_ index: Int) -> CGSize {
        let spread = size * (isOpen ? 0.58 : 0.08)
        return switch index {
        case 0: CGSize(width: -spread, height: -spread * 0.95)
        case 1: CGSize(width: spread * 0.95, height: -spread * 1.15)
        default: CGSize(width: size * 0.08, height: -spread * 1.5)
        }
    }

    /// Confettis projetés par l'ouverture.
    private var confetti: some View {
        ForEach(0..<12, id: \.self) { index in
            ConfettiPiece(index: index, size: size, isOpen: isOpen, isBursting: phase == .bursting)
        }
    }
}

/// Un confetti : rond ou rectangle selon son rang, projeté vers le haut puis retombant.
/// Vue séparée pour garder l'inférence de types rapide côté compilateur.
private struct ConfettiPiece: View {
    let index: Int
    let size: CGFloat
    let isOpen: Bool
    let isBursting: Bool

    private var isRound: Bool { index.isMultiple(of: 3) }

    private var color: Color {
        let palette: [Color] = [.Theme.primary, .Theme.accentAmber, .Theme.secondary,
                                .Theme.heart, .Theme.pastelMint, .Theme.pastelLavender]
        return palette[index % palette.count]
    }

    private var offset: CGSize {
        // Éventail vers le haut uniquement : les confettis jaillissent de l'ouverture
        // de la boîte, jamais par-derrière.
        let angle = .pi + (Double(index) / 11) * .pi
        let distance: CGFloat = size * (isBursting ? 1.1 : (isOpen ? 0.45 : 0.04))
        let fall: CGFloat = isBursting ? size * 0.55 : 0
        let mouth = -size * 0.12  // hauteur de l'ouverture de la boîte
        let x = CGFloat(cos(angle)) * distance
        let y = CGFloat(sin(angle)) * distance + mouth + fall
        return CGSize(width: x, height: y)
    }

    var body: some View {
        shape
            .frame(width: size * 0.075, height: size * (isRound ? 0.075 : 0.12))
            .rotationEffect(.degrees(isBursting ? Double(index) * 47 : 0))
            .offset(offset)
            // Ils restent visibles pendant la chute et ne s'effacent qu'à la fin.
            .opacity(isOpen ? (isBursting ? 0.3 : 1) : 0)
    }

    @ViewBuilder
    private var shape: some View {
        if isRound {
            Circle().fill(color)
        } else {
            RoundedRectangle(cornerRadius: 1.5, style: .continuous).fill(color)
        }
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
