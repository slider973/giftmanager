import SwiftUI

/// Animation de démarrage : un cadeau frétille, son couvercle s'envole, une lueur sort de la
/// boîte et des confettis jaillissent. Jouée une seule fois, puis `onFinished` est appelé.
///
/// Leçon tirée de `GiftLoadingView` : découper l'illustration 3D `gift_red` en deux morceaux
/// se voyait, et enchaîner des étapes par `Task.sleep` saccadait. Ici le cadeau est dessiné en
/// formes vectorielles (couvercle et boîte séparés par construction) et tout le mouvement
/// découle d'une seule horloge continue (`TimelineView`) : chaque image est une fonction
/// pure du temps écoulé, rien n'est jamais relancé.
///
/// « Réduire les animations » remplace la séquence par le cadeau immobile, plus brièvement.
/// Un toucher passe l'animation.
struct GiftOpeningSplash: View {
    /// Taille du cadeau ; tout le reste est proportionnel.
    var size: CGFloat = 150
    var onFinished: () -> Void = {}

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var start = Date()

    var body: some View {
        content
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .fcScreenBackground()
            .contentShape(Rectangle())
            .onTapGesture { onFinished() }
            .accessibilityElement(children: .ignore)
            .accessibilityLabel("Gift Manager")
            .accessibilityAddTraits(.isImage)
            .task {
                let duration = reduceMotion ? Timing.reducedDuration : Timing.duration
                try? await Task.sleep(for: .seconds(duration))
                guard !Task.isCancelled else { return }
                onFinished()
            }
    }

    @ViewBuilder
    private var content: some View {
        if reduceMotion {
            scene(at: Timing.restingTime)
        } else {
            TimelineView(.animation) { timeline in
                scene(at: timeline.date.timeIntervalSince(start))
            }
        }
    }

    // MARK: - Image à l'instant t

    /// Toute la scène à l'instant `t` (secondes depuis l'apparition).
    private func scene(at t: TimeInterval) -> some View {
        let state = SplashState(t: t)
        return VStack(spacing: Spacing.xxl) {
            ZStack {
                glow(state)
                confetti(state)
                gift(state)
            }
            .frame(width: size * 2.2, height: size * 2.2)

            Text("Gift Manager")
                .font(Font.Theme.title)
                .foregroundStyle(Color.Theme.textPrimary)
                .opacity(state.title)
                .offset(y: (1 - state.title) * Spacing.m)
        }
    }

    private func gift(_ state: SplashState) -> some View {
        ZStack {
            GiftBox(size: size)
                .scaleEffect(x: 1 + state.squash, y: 1 - state.squash, anchor: .bottom)
                .offset(y: size * 0.2)

            GiftLid(size: size)
                .rotationEffect(.degrees(-22 * state.lift))
                .offset(x: size * 0.16 * state.lift,
                        y: -size * 0.17 - size * 0.62 * state.lift + size * state.squash * 0.5)
        }
        .frame(width: size, height: size)
        .rotationEffect(.degrees(state.wiggle), anchor: .bottom)
        .scaleEffect(state.appear)
        .opacity(min(1, state.appear * 1.5))
    }

    /// Lueur chaude qui monte de l'ouverture de la boîte.
    private func glow(_ state: SplashState) -> some View {
        Circle()
            .fill(RadialGradient(
                colors: [Color.Theme.accentAmber.opacity(0.45), Color.Theme.accentAmber.opacity(0)],
                center: .center, startRadius: 0, endRadius: size * 0.75
            ))
            .frame(width: size * 1.5, height: size * 1.5)
            .scaleEffect(0.3 + 0.7 * state.glow)
            .opacity(state.glow)
            .offset(y: -size * 0.1)
    }

    private func confetti(_ state: SplashState) -> some View {
        ZStack {
            ForEach(Confetto.all) { piece in
                let p = piece.position(after: state.burst, size: size)
                RoundedRectangle(cornerRadius: size * 0.008, style: .continuous)
                    .fill(piece.color)
                    .frame(width: size * piece.length, height: size * 0.028)
                    .rotationEffect(.degrees(piece.spin * state.burst))
                    .offset(x: p.x, y: p.y - size * 0.1)
                    .opacity(piece.opacity(after: state.burst))
            }
        }
    }
}

// MARK: - Chronologie

private enum Timing {
    /// Durée de la séquence complète avant `onFinished`.
    static let duration: TimeInterval = 2.2
    /// Avec « Réduire les animations » : cadeau immobile, affiché brièvement.
    static let reducedDuration: TimeInterval = 0.8
    /// Instant montré figé avec « Réduire les animations » : cadeau posé, encore fermé.
    static let restingTime: TimeInterval = 0.45

    static let appearEnd: TimeInterval = 0.45
    static let wiggleEnd: TimeInterval = 0.95
    static let liftEnd: TimeInterval = 1.35
}

/// Valeurs d'animation dérivées du temps écoulé. Fonction pure : aucune étape à enchaîner.
private struct SplashState {
    var appear: Double = 0
    var wiggle: Double = 0
    var squash: Double = 0
    var lift: Double = 0
    var glow: Double = 0
    var title: Double = 0
    /// Secondes écoulées depuis l'ouverture (0 avant).
    var burst: Double = 0

    init(t: TimeInterval) {
        appear = Ease.outBack(progress(t, 0, Timing.appearEnd))

        // Frétillement qui s'amortit, puis le cadeau se tasse juste avant de s'ouvrir.
        let w = progress(t, Timing.appearEnd, Timing.wiggleEnd)
        if w > 0 && w < 1 {
            wiggle = 7 * sin(2 * .pi * 2.5 * w) * (1 - w)
        }
        let squeeze = progress(t, Timing.appearEnd + 0.3, Timing.wiggleEnd)
        let release = progress(t, Timing.wiggleEnd, Timing.wiggleEnd + 0.2)
        squash = 0.07 * Ease.inOut(squeeze) * (1 - Ease.outCubic(release))

        lift = Ease.outBack(progress(t, Timing.wiggleEnd, Timing.liftEnd))
        glow = Ease.outCubic(progress(t, Timing.wiggleEnd, Timing.wiggleEnd + 0.55))
        title = Ease.outCubic(progress(t, Timing.liftEnd - 0.15, Timing.liftEnd + 0.35))
        burst = max(0, t - Timing.wiggleEnd)
    }

    /// Avancement de 0 à 1 entre `from` et `to`, borné.
    private func progress(_ t: Double, _ from: Double, _ to: Double) -> Double {
        min(1, max(0, (t - from) / (to - from)))
    }
}

private enum Ease {
    static func outCubic(_ x: Double) -> Double { 1 - pow(1 - x, 3) }

    static func inOut(_ x: Double) -> Double {
        x < 0.5 ? 4 * x * x * x : 1 - pow(-2 * x + 2, 3) / 2
    }

    /// Dépasse légèrement la cible avant de s'y poser : donne du rebond.
    static func outBack(_ x: Double) -> Double {
        let c1 = 1.70158
        let c3 = c1 + 1
        return 1 + c3 * pow(x - 1, 3) + c1 * pow(x - 1, 2)
    }
}

// MARK: - Confettis

/// Un confetti : trajectoire balistique déterministe (mêmes confettis à chaque lancement).
private struct Confetto: Identifiable {
    let id: Int
    /// Angle de départ en radians (vers le haut : entre -π et 0).
    let angle: Double
    /// Vitesse initiale, en tailles de cadeau par seconde.
    let speed: Double
    let length: Double
    let spin: Double
    let color: Color

    /// Couleurs vives lisibles en clair comme en sombre.
    private static let palette: [Color] = [
        Color.Theme.heart, Color.Theme.accentAmber, Color.Theme.secondary,
        Color.Theme.mineFg, Color.Theme.ownedFg, Color.Theme.potFg,
    ]

    static let all: [Confetto] = (0..<16).map { i in
        let spread = Double(i) / 15
        let jitter = sin(Double(i) * 12.9898) * 0.5 + 0.5   // pseudo-aléatoire stable
        return Confetto(
            id: i,
            angle: -.pi * (0.12 + 0.76 * spread) + (jitter - 0.5) * 0.25,
            speed: 1.25 + jitter * 0.8,
            length: 0.05 + jitter * 0.04,
            spin: (i.isMultiple(of: 2) ? 1 : -1) * (360 + jitter * 360),
            color: palette[i % palette.count]
        )
    }

    private static let gravity = 2.4

    func position(after t: Double, size: CGFloat) -> CGPoint {
        // Freinage de l'air : la vitesse horizontale s'essouffle.
        let drag = 1 - exp(-2.2 * t)
        let x = cos(angle) * speed * drag / 2.2
        let y = sin(angle) * speed * t + 0.5 * Self.gravity * t * t
        return CGPoint(x: x * size, y: y * size)
    }

    func opacity(after t: Double) -> Double {
        guard t > 0 else { return 0 }
        return 1 - min(1, max(0, (t - 0.6) / 0.5))
    }
}

// MARK: - Dessin du cadeau

/// Corps de la boîte, ruban vertical compris.
private struct GiftBox: View {
    let size: CGFloat

    var body: some View {
        let shape = RoundedRectangle(cornerRadius: size * 0.07, style: .continuous)
        ZStack {
            shape.fill(Color.Theme.pastelPeach)
            // Ombre sous le couvercle et léger dégradé pour le volume.
            shape.fill(LinearGradient(
                colors: [Color.Theme.textPrimary.opacity(0.14), Color.Theme.textPrimary.opacity(0.02)],
                startPoint: .top, endPoint: .center
            ))
            Rectangle()
                .fill(Color.Theme.heart)
                .frame(width: size * 0.14)
        }
        .clipShape(shape)
        .frame(width: size * 0.76, height: size * 0.56)
    }
}

/// Couvercle avec son ruban et son nœud : ils s'envolent ensemble.
private struct GiftLid: View {
    let size: CGFloat

    var body: some View {
        let shape = RoundedRectangle(cornerRadius: size * 0.05, style: .continuous)
        ZStack {
            ZStack {
                shape.fill(Color.Theme.pastelPeach)
                shape.fill(LinearGradient(
                    colors: [Color.white.opacity(0.25), Color.white.opacity(0)],
                    startPoint: .top, endPoint: .bottom
                ))
                Rectangle()
                    .fill(Color.Theme.heart)
                    .frame(width: size * 0.14)
            }
            .clipShape(shape)
            .frame(width: size * 0.88, height: size * 0.18)

            bow.offset(y: -size * 0.15)
        }
    }

    private var bow: some View {
        ZStack {
            loop.rotationEffect(.degrees(-32)).offset(x: -size * 0.12)
            loop.rotationEffect(.degrees(32)).offset(x: size * 0.12)
            Circle()
                .fill(Color.Theme.heart)
                .frame(width: size * 0.12, height: size * 0.12)
                .offset(y: size * 0.04)
        }
    }

    private var loop: some View {
        Ellipse()
            .strokeBorder(Color.Theme.heart, lineWidth: size * 0.06)
            .frame(width: size * 0.28, height: size * 0.17)
    }
}

#Preview("Démarrage") {
    GiftOpeningSplash()
}
