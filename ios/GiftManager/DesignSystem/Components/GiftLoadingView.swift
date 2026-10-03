import SwiftUI

/// Animation de chargement : un paquet cadeau qui tremble, puis s'ouvre.
///
/// Dessinée entièrement en SwiftUI plutôt qu'avec des PNG : les couleurs suivent le thème
/// (donc le mode sombre), le rendu reste net sur tous les écrans et l'app ne s'alourdit pas.
/// Les couches reprennent le découpage classique d'un paquet : boîte, couvercle, ruban, nœud,
/// étincelles, confettis.
///
/// Séquence : tremblement → le couvercle s'envole → lumière → confettis → reprise.
/// « Réduire les animations » (Réglages iOS) remplace le tout par une respiration douce.
struct GiftLoadingView: View {
    /// Taille de la boîte ; tout le reste est proportionnel.
    var size: CGFloat = 96
    /// Texte optionnel sous le cadeau.
    var label: String?

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var phase = Phase.idle

    /// Étapes du cycle, avec leur durée.
    private enum Phase: CaseIterable {
        case idle, shaking, opening, bursting

        var duration: Double {
            switch self {
            case .idle: 0.35
            case .shaking: 0.9
            case .opening: 0.55
            case .bursting: 0.9
            }
        }
    }

    private var isOpen: Bool { phase == .opening || phase == .bursting }

    var body: some View {
        VStack(spacing: Spacing.l) {
            ZStack {
                glow
                confetti
                sparkles
                box
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
            guard !reduceMotion else { return }
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
        }
    }

    private func animation(for phase: Phase) -> Animation {
        switch phase {
        case .idle: .easeOut(duration: 0.3)
        case .shaking: .easeInOut(duration: 0.1).repeatCount(8, autoreverses: true)
        case .opening: .spring(response: 0.45, dampingFraction: 0.55)
        case .bursting: .easeOut(duration: 0.8)
        }
    }

    // MARK: - Couches

    private var box: some View {
        ZStack(alignment: .bottom) {
            GiftBase(size: size)
                // Le paquet s'écrase légèrement quand le couvercle part : il « souffle ».
                .scaleEffect(x: isOpen ? 1.06 : 1, y: isOpen ? 0.94 : 1, anchor: .bottom)

            GiftLid(size: size)
                .offset(y: lidOffset)
                .rotationEffect(.degrees(isOpen ? -14 : 0), anchor: .bottomLeading)
                // Il s'éloigne en s'estompant, au lieu de s'effacer d'un coup.
                .opacity(phase == .bursting ? 0.45 : 1)
                .scaleEffect(phase == .bursting ? 0.85 : 1)
        }
        .rotationEffect(.degrees(phase == .shaking ? 3 : 0), anchor: .bottom)
        .scaleEffect(reduceMotion ? 1 : (phase == .idle ? 1 : 1.02))
        // Respiration douce quand les animations sont réduites.
        .opacity(reduceMotion ? 0.9 : 1)
    }

    /// Hauteur dont le couvercle s'éloigne de la boîte.
    private var lidOffset: CGFloat {
        switch phase {
        case .idle, .shaking: -size * 0.52
        case .opening: -size * 0.95
        case .bursting: -size * 1.35
        }
    }

    /// Lumière qui jaillit de la boîte ouverte.
    private var glow: some View {
        Circle()
            .fill(
                RadialGradient(colors: [Color.Theme.accentAmber.opacity(0.55), .clear],
                               center: .center, startRadius: 0, endRadius: size * 0.9)
            )
            .frame(width: size * 1.8, height: size * 1.8)
            .scaleEffect(isOpen ? 1 : 0.2)
            .opacity(isOpen ? 1 : 0)
            .offset(y: -size * 0.15)
            .blur(radius: 6)
    }

    private var sparkles: some View {
        ForEach(0..<3, id: \.self) { index in
            Sparkle()
                .fill(Color.Theme.accentAmber)
                .frame(width: size * 0.22, height: size * 0.22)
                .offset(sparkleOffset(index))
                .scaleEffect(isOpen ? 1 : 0.1)
                .opacity(isOpen ? 1 : 0)
        }
    }

    private func sparkleOffset(_ index: Int) -> CGSize {
        let spread = size * (isOpen ? 0.62 : 0.1)
        return switch index {
        case 0: CGSize(width: -spread, height: -spread * 0.9)
        case 1: CGSize(width: spread * 0.9, height: -spread * 1.1)
        default: CGSize(width: 0, height: -spread * 1.4)
        }
    }

    /// Confettis projetés à l'ouverture, dans les couleurs pastel du thème.
    private var confetti: some View {
        ForEach(0..<10, id: \.self) { index in
            ConfettiPiece(index: index, size: size, isOpen: isOpen, isBursting: phase == .bursting)
        }
    }
}

/// Un confetti : rond ou rectangle selon son rang, projeté en éventail puis retombant.
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
        // de la boîte, jamais par-derrière. L'angle balaie un demi-tour au-dessus.
        let angle = .pi + (Double(index) / 9) * .pi
        let distance: CGFloat = size * (isBursting ? 1.25 : (isOpen ? 0.5 : 0.05))
        let fall: CGFloat = isBursting ? size * 0.5 : 0
        let mouth = -size * 0.45  // hauteur du bord supérieur de la boîte
        let x = CGFloat(cos(angle)) * distance
        let y = CGFloat(sin(angle)) * distance + mouth + fall
        return CGSize(width: x, height: y)
    }

    var body: some View {
        shape
            .frame(width: size * 0.09, height: size * (isRound ? 0.09 : 0.14))
            .rotationEffect(.degrees(isBursting ? Double(index) * 54 : 0))
            .offset(offset)
            // Ils restent visibles pendant l'éclatement et ne s'effacent qu'en fin de chute.
            .opacity(isOpen ? (isBursting ? 0.35 : 1) : 0)
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

// MARK: - Formes

/// Corps du paquet, avec son ruban vertical.
private struct GiftBase: View {
    let size: CGFloat

    var body: some View {
        ZStack {
            RoundedRectangle(cornerRadius: size * 0.1, style: .continuous)
                .fill(
                    LinearGradient(colors: [Color.Theme.primary, Color.Theme.primary.opacity(0.82)],
                                   startPoint: .topLeading, endPoint: .bottomTrailing)
                )
            Rectangle()
                .fill(Color.Theme.accentAmber)
                .frame(width: size * 0.16)
        }
        .frame(width: size, height: size * 0.78)
        .clipShape(RoundedRectangle(cornerRadius: size * 0.1, style: .continuous))
        .shadow(color: .black.opacity(0.18), radius: size * 0.06, y: size * 0.04)
    }
}

/// Couvercle et son nœud.
private struct GiftLid: View {
    let size: CGFloat

    var body: some View {
        ZStack {
            Bow(size: size)
                .offset(y: -size * 0.2)
            ZStack {
                RoundedRectangle(cornerRadius: size * 0.08, style: .continuous)
                    .fill(
                        LinearGradient(colors: [Color.Theme.primary.opacity(0.95), Color.Theme.primary],
                                       startPoint: .top, endPoint: .bottom)
                    )
                Rectangle()
                    .fill(Color.Theme.accentAmber)
                    .frame(width: size * 0.16)
            }
            .frame(width: size * 1.12, height: size * 0.26)
            .clipShape(RoundedRectangle(cornerRadius: size * 0.08, style: .continuous))
        }
        .shadow(color: .black.opacity(0.14), radius: size * 0.04, y: size * 0.02)
    }
}

/// Nœud : deux boucles et un centre.
private struct Bow: View {
    let size: CGFloat

    var body: some View {
        ZStack {
            Ellipse()
                .fill(Color.Theme.accentAmber)
                .frame(width: size * 0.3, height: size * 0.22)
                .rotationEffect(.degrees(-28))
                .offset(x: -size * 0.14)
            Ellipse()
                .fill(Color.Theme.accentAmber)
                .frame(width: size * 0.3, height: size * 0.22)
                .rotationEffect(.degrees(28))
                .offset(x: size * 0.14)
            Circle()
                .fill(Color.Theme.accentAmber)
                .frame(width: size * 0.13)
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
    GiftLoadingView(label: "Chargement de la liste…")
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .fcScreenBackground()
}
