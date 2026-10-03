import SwiftUI

/// Mode enfant (#36) : sur le téléphone du parent, l'enfant parcourt ses envies en grand
/// et pose des cœurs « très envie ». Aucun prix, statut, réservation, cagnotte, idée ni
/// bouton de gestion. Sortie protégée : appui long sur « Quitter », puis Face ID / code.
struct ChildModeView: View {
    @State private var model: ChildModeModel
    let onFinish: (_ didChange: Bool) -> Void

    @Environment(\.dynamicTypeSize) private var dynamicTypeSize
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var hint: Hint?
    @State private var bursts: [UUID: Int] = [:]

    private enum Hint: Equatable {
        case exit, saveFailed

        var text: String {
            switch self {
            case .exit: "Pour quitter, un adulte garde le doigt sur « Quitter » pendant 3 secondes."
            case .saveFailed: "Oups, ce cœur n'a pas été enregistré. Réessaie !"
            }
        }

        var systemImage: String {
            switch self {
            case .exit: "hand.raised.fill"
            case .saveFailed: "wifi.exclamationmark"
            }
        }
    }

    init(child: Child, wishes: [WishItem], repository: GiftRepository, onFinish: @escaping (_ didChange: Bool) -> Void) {
        _model = State(initialValue: ChildModeModel(child: child, wishes: wishes) { id, favorite in
            try await repository.setPriority(itemId: id, favorite: favorite)
        })
        self.onFinish = onFinish
    }

    init(model: ChildModeModel, onFinish: @escaping (_ didChange: Bool) -> Void) {
        _model = State(initialValue: model)
        self.onFinish = onFinish
    }

    private var columns: [GridItem] {
        let count = dynamicTypeSize.isAccessibilitySize ? 1 : 2
        return Array(repeating: GridItem(.flexible(), spacing: Spacing.l, alignment: .top), count: count)
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: Spacing.xl) {
                header
                if model.items.isEmpty {
                    EmptyStateView(imageName: "empty_box", title: "Pas encore d'envies",
                                   message: "Avec ton parent, ajoute des cadeaux à ta liste.")
                } else {
                    LazyVGrid(columns: columns, spacing: Spacing.l) {
                        ForEach(model.items) { item in
                            ChildModeCard(item: item, burst: bursts[item.id]) { toggle(item) }
                        }
                    }
                }
            }
            .padding(.horizontal, Spacing.xl)
            .padding(.top, Spacing.s)
            .padding(.bottom, Spacing.xxl * 2)
        }
        .scrollIndicators(.hidden)
        .safeAreaInset(edge: .top, spacing: 0) { topBar }
        .overlay(alignment: .bottom) { hintBanner }
        .fcScreenBackground()
        .statusBarHidden()
        .persistentSystemOverlays(.hidden)
        .interactiveDismissDisabled()
        .task(id: hint) {
            guard hint != nil else { return }
            try? await Task.sleep(for: .seconds(3.5))
            withAnimation(.easeOut(duration: 0.25)) { hint = nil }
        }
    }

    // MARK: - Barre du haut

    private var topBar: some View {
        HStack(spacing: Spacing.m) {
            heartCounter
            Spacer(minLength: Spacing.s)
            ChildModeExitButton {
                withAnimation(.spring(duration: 0.35)) { hint = .exit }
            } onUnlock: {
                if await ParentAuthenticator.authenticate() { onFinish(model.didChange) }
            }
        }
        .padding(.horizontal, Spacing.xl)
        .padding(.vertical, Spacing.s)
        .background(Color.Theme.background.opacity(0.94).ignoresSafeArea(edges: .top))
    }

    private var heartCounter: some View {
        HStack(spacing: Spacing.xs) {
            Image(systemName: "heart.fill")
                .foregroundStyle(Color.Theme.heart)
                .symbolEffect(.bounce, value: model.favoriteCount)
            Text("\(model.favoriteCount)")
                .monospacedDigit()
                .contentTransition(.numericText())
                .foregroundStyle(Color.Theme.textPrimary)
        }
        .font(.system(.headline, design: .rounded, weight: .bold))
        .padding(.horizontal, Spacing.m)
        .frame(minHeight: HitTarget.minimum)
        .background(Color.Theme.surface, in: Capsule())
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(model.favoriteCount > 1 ? "\(model.favoriteCount) cœurs posés" : "\(model.favoriteCount) cœur posé")
    }

    // MARK: - En-tête

    private var header: some View {
        HStack(alignment: .center, spacing: Spacing.m) {
            VStack(alignment: .leading, spacing: Spacing.s) {
                ChildAvatar(name: model.child.firstName, emoji: model.child.avatarEmoji,
                            colorName: model.child.avatarColor, size: 56)
                    .accessibilityHidden(true)
                Text("Les envies de \(model.child.firstName)")
                    .font(Font.Theme.largeTitle)
                    .foregroundStyle(Color.Theme.textPrimary)
                    .fixedSize(horizontal: false, vertical: true)
                    .accessibilityAddTraits(.isHeader)
                Text("Touche le cœur des cadeaux dont tu as très envie !")
                    .font(.system(.title3, design: .rounded, weight: .medium))
                    .foregroundStyle(Color.Theme.textSecondary)
                    .fixedSize(horizontal: false, vertical: true)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            if !dynamicTypeSize.isAccessibilitySize {
                Image("mascot_love")
                    .resizable()
                    .scaledToFit()
                    .frame(width: 112)
                    .accessibilityHidden(true)
            }
        }
    }

    // MARK: - Messages

    @ViewBuilder
    private var hintBanner: some View {
        if let hint {
            Label(hint.text, systemImage: hint.systemImage)
                .font(Font.Theme.callout.weight(.semibold))
                .foregroundStyle(Color.Theme.onPrimary)
                .padding(.horizontal, Spacing.l)
                .padding(.vertical, Spacing.m)
                .background(Color.Theme.primary, in: RoundedRectangle(cornerRadius: Radius.card, style: .continuous))
                .padding(.horizontal, Spacing.xl)
                .padding(.bottom, Spacing.l)
                .transition(.move(edge: .bottom).combined(with: .opacity))
                .onTapGesture { withAnimation { self.hint = nil } }
                .accessibilityAddTraits(.isStaticText)
                .onAppear { UIAccessibility.post(notification: .announcement, argument: hint.text) }
        }
    }

    // MARK: - Actions

    private func toggle(_ item: ChildModeItem) {
        if !item.isFavorite && !reduceMotion { bursts[item.id, default: 0] += 1 }
        Task {
            do {
                try await model.toggleFavorite(item.id)
            } catch {
                withAnimation(.spring(duration: 0.35)) { hint = .saveFailed }
            }
        }
    }
}

// MARK: - Carte

/// Grande carte photo du mode enfant : toute la carte bascule le cœur.
private struct ChildModeCard: View {
    let item: ChildModeItem
    let burst: Int?
    let action: () -> Void

    @Environment(\.dynamicTypeSize) private var dynamicTypeSize
    @ScaledMetric(relativeTo: .title3) private var heartSize: CGFloat = 52

    private static let radius: CGFloat = 28

    var body: some View {
        Button(action: action) {
            VStack(alignment: .leading, spacing: 0) {
                photo
                title
            }
            .background(Color.Theme.surface)
            .clipShape(RoundedRectangle(cornerRadius: Self.radius, style: .continuous))
            .overlay {
                RoundedRectangle(cornerRadius: Self.radius, style: .continuous)
                    .strokeBorder(item.isFavorite ? Color.Theme.heart : Color.clear, lineWidth: 3)
            }
            .overlay(alignment: .topTrailing) { heart.padding(Spacing.s) }
            .shadow(color: .black.opacity(0.06), radius: 4, x: 0, y: 2)
            .contentShape(RoundedRectangle(cornerRadius: Self.radius, style: .continuous))
            .animation(.spring(response: 0.3, dampingFraction: 0.7), value: item.isFavorite)
        }
        .buttonStyle(FCPressableStyle(pressedScale: 0.94))
        .sensoryFeedback(.impact(weight: .light), trigger: item.isFavorite)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(item.title)
        .accessibilityValue(item.isFavorite ? "Très envie" : "")
        .accessibilityHint(item.isFavorite ? "Retire le cœur" : "Pose un cœur : tu en as très envie")
        .accessibilityAddTraits(item.isFavorite ? [.isButton, .isSelected] : .isButton)
    }

    private var photo: some View {
        Color.Theme.surface
            .aspectRatio(1, contentMode: .fit)
            .overlay {
                RemoteImage(url: item.imageURL, contentMode: .fit, placeholderSeed: item.title)
                    .padding(item.imageURL == nil ? 0 : Spacing.s)
            }
            .clipped()
    }

    @ViewBuilder
    private var title: some View {
        let text = Text(item.title)
            .font(.system(.title3, design: .rounded, weight: .bold))
            .foregroundStyle(Color.Theme.textPrimary)
        Group {
            if dynamicTypeSize.isAccessibilitySize {
                text
            } else {
                // Deux lignes réservées : toutes les cartes d'une rangée ont la même hauteur.
                text.lineLimit(2, reservesSpace: true)
            }
        }
        .multilineTextAlignment(.leading)
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.horizontal, Spacing.m)
        .padding(.top, Spacing.s)
        .padding(.bottom, Spacing.m)
    }

    private var heart: some View {
        ZStack {
            Circle()
                .fill(Color.Theme.surface)
                .shadow(color: .black.opacity(0.08), radius: 4, x: 0, y: 2)
            Image(systemName: item.isFavorite ? "heart.fill" : "heart")
                .font(.system(size: heartSize * 0.46, weight: .bold))
                .foregroundStyle(item.isFavorite ? Color.Theme.heart : Color.Theme.textSecondary)
                .contentTransition(.symbolEffect(.replace))
                .symbolEffect(.bounce, value: item.isFavorite)
        }
        .frame(width: heartSize, height: heartSize)
        .overlay {
            if let burst { HeartBurst().id(burst) }
        }
    }
}

/// Petite gerbe de cœurs quand l'enfant pose un cœur (absente si Réduire les animations).
private struct HeartBurst: View {
    @State private var isOut = false
    private let count = 6

    var body: some View {
        ZStack {
            ForEach(0..<count, id: \.self) { index in
                let angle = Double(index) / Double(count) * 2 * .pi - .pi / 2
                Image(systemName: "heart.fill")
                    .font(.system(size: index.isMultiple(of: 2) ? 14 : 10, weight: .bold))
                    .foregroundStyle(Color.Theme.heart)
                    .offset(x: isOut ? cos(angle) * 48 : 0, y: isOut ? sin(angle) * 48 : 0)
                    .scaleEffect(isOut ? 1 : 0.4)
                    .opacity(isOut ? 0 : 1)
            }
        }
        .allowsHitTesting(false)
        .accessibilityHidden(true)
        .onAppear {
            withAnimation(.easeOut(duration: 0.7)) { isOut = true }
        }
    }
}

// MARK: - Previews

#if DEBUG
@MainActor
private enum ChildModePreview {
    static let child = Child(id: UUID(), householdId: UUID(), firstName: "Léo", birthdate: nil,
                             avatarEmoji: "🦖", avatarColor: "pastelMint", avatarUrl: nil)

    static func wish(_ title: String, favorite: Bool = false) -> WishItem {
        WishItem(id: UUID(), childId: child.id, eventId: nil, kind: .wish, title: title, notes: nil,
                 imageUrl: nil, priority: favorite ? 1 : 0, position: 0, owned: false, createdBy: nil,
                 status: nil, myReservation: nil)
    }

    static var model: ChildModeModel {
        ChildModeModel(child: child, wishes: [
            wish("LEGO Technic McLaren F1", favorite: true),
            wish("Casque Sony WH-1000XM5"),
            wish("Nintendo Switch OLED"),
            wish("Maillot PSG 2025 avec son nom au dos"),
        ]) { _, _ in try await Task.sleep(for: .milliseconds(300)) }
    }
}

#Preview("Clair") {
    ChildModeView(model: ChildModePreview.model) { _ in }
}

#Preview("Sombre") {
    ChildModeView(model: ChildModePreview.model) { _ in }
        .preferredColorScheme(.dark)
}

#Preview("Texte XXXL") {
    ChildModeView(model: ChildModePreview.model) { _ in }
        .dynamicTypeSize(.accessibility2)
}
#endif
