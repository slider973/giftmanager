import LocalAuthentication
import SwiftUI

/// Vérification de l'adulte à la sortie du mode enfant : Face ID, Touch ID ou code de l'appareil.
enum ParentAuthenticator {
    /// `true` si l'adulte est vérifié, ou si l'appareil n'a aucun code configuré
    /// (l'appui long reste alors la seule protection). `false` si l'authentification échoue ou est annulée.
    static func authenticate() async -> Bool {
        let context = LAContext()
        context.localizedCancelTitle = "Rester en mode enfant"
        var error: NSError?
        guard context.canEvaluatePolicy(.deviceOwnerAuthentication, error: &error) else { return true }
        do {
            return try await context.evaluatePolicy(.deviceOwnerAuthentication,
                                                    localizedReason: "Quitter le mode enfant")
        } catch {
            return false
        }
    }
}

/// Bouton « Quitter » du mode enfant : il faut le **maintenir** `holdDuration` secondes.
/// Un anneau se remplit pendant l'appui ; relâché trop tôt, il se vide et `onTooShort` est appelé.
/// Une fois l'anneau plein, `onUnlock` demande Face ID / le code avant de fermer.
struct ChildModeExitButton: View {
    static let holdDuration: Double = 3

    let onTooShort: () -> Void
    let onUnlock: () async -> Void

    @State private var progress: CGFloat = 0
    @State private var isHolding = false
    @State private var isUnlocking = false
    @State private var completions = 0
    @ScaledMetric(relativeTo: .caption) private var ringSize: CGFloat = 28

    var body: some View {
        HStack(spacing: Spacing.s) {
            ZStack {
                Circle()
                    .stroke(Color.Theme.separator, lineWidth: 3)
                Circle()
                    .trim(from: 0, to: progress)
                    .stroke(Color.Theme.primary, style: StrokeStyle(lineWidth: 3, lineCap: .round))
                    .rotationEffect(.degrees(-90))
                Image(systemName: "lock.fill")
                    .font(.system(size: ringSize * 0.42, weight: .bold))
                    .foregroundStyle(Color.Theme.primary)
            }
            .frame(width: ringSize, height: ringSize)

            Text(isHolding || isUnlocking ? "Maintiens…" : "Quitter")
                .font(Font.Theme.captionBold)
                .foregroundStyle(Color.Theme.textPrimary)
                .contentTransition(.opacity)
        }
        .padding(.leading, Spacing.s)
        .padding(.trailing, Spacing.m)
        .padding(.vertical, Spacing.xs)
        .frame(minHeight: HitTarget.minimum)
        .background {
            Capsule()
                .fill(Color.Theme.surface)
                .shadow(color: .black.opacity(0.06), radius: 4, x: 0, y: 2)
        }
        .contentShape(Capsule())
        .onLongPressGesture(minimumDuration: Self.holdDuration, maximumDistance: 40) {
            completions += 1
            Task { await unlock() }
        } onPressingChanged: { pressing in
            pressingChanged(pressing)
        }
        .sensoryFeedback(.impact(weight: .medium), trigger: completions)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("Quitter le mode enfant")
        .accessibilityHint("Réservé aux adultes : maintiens le doigt appuyé trois secondes.")
        .accessibilityAddTraits(.isButton)
        // VoiceOver : l'activation passe directement à Face ID ou au code.
        .accessibilityAction { Task { await unlock() } }
    }

    private func pressingChanged(_ pressing: Bool) {
        guard !isUnlocking else { return }
        isHolding = pressing
        if pressing {
            withAnimation(.linear(duration: Self.holdDuration)) { progress = 1 }
        } else {
            let before = completions
            // Laisse à l'appui long le temps de se conclure avant de juger l'appui « trop court ».
            Task {
                try? await Task.sleep(for: .milliseconds(120))
                guard completions == before, !isUnlocking else { return }
                withAnimation(.easeOut(duration: 0.25)) { progress = 0 }
                onTooShort()
            }
        }
    }

    private func unlock() async {
        guard !isUnlocking else { return }
        isUnlocking = true
        progress = 1
        await onUnlock()
        isUnlocking = false
        isHolding = false
        withAnimation(.easeOut(duration: 0.25)) { progress = 0 }
    }
}

#Preview("Clair") {
    ChildModeExitButton(onTooShort: {}, onUnlock: {})
        .padding(Spacing.xl)
        .fcScreenBackground()
}

#Preview("Sombre") {
    ChildModeExitButton(onTooShort: {}, onUnlock: {})
        .padding(Spacing.xl)
        .fcScreenBackground()
        .preferredColorScheme(.dark)
}
