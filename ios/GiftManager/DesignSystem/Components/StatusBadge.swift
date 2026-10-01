import SwiftUI

/// Statut d'un cadeau tel que vu par un membre de la famille.
///
/// Mode surprise : quand un parent regarde la liste de son propre enfant,
/// l'écran passe `nil` au lieu d'un `GiftStatus` et **aucun** badge n'est affiché.
enum GiftStatus: Equatable, CaseIterable {
    case available
    case taken
    case mine
    case owned

    var label: String {
        switch self {
        case .available: "Disponible"
        case .taken: "Déjà pris"
        case .mine: "Je l'offre"
        case .owned: "Possède déjà"
        }
    }

    var background: Color {
        switch self {
        case .available: Color.Theme.availableBg
        case .taken: Color.Theme.takenBg
        case .mine: Color.Theme.mineBg
        case .owned: Color.Theme.ownedBg
        }
    }

    var foreground: Color {
        switch self {
        case .available: Color.Theme.availableFg
        case .taken: Color.Theme.takenFg
        case .mine: Color.Theme.mineFg
        case .owned: Color.Theme.ownedFg
        }
    }
}

/// Pastille capsule pastel portant le statut d'un cadeau.
struct StatusBadge: View {
    let status: GiftStatus

    var body: some View {
        Text(status.label)
            .font(Font.Theme.captionBold)
            .foregroundStyle(status.foreground)
            .lineLimit(1)
            .padding(.horizontal, Spacing.s + 2)
            .padding(.vertical, Spacing.xs)
            .background(status.background, in: Capsule())
            .fixedSize()
            .accessibilityElement(children: .ignore)
            .accessibilityLabel("Statut : \(status.label)")
    }
}

#Preview("Clair") {
    VStack(alignment: .leading, spacing: Spacing.m) {
        ForEach(GiftStatus.allCases, id: \.self) { StatusBadge(status: $0) }
    }
    .padding(Spacing.xl)
    .fcScreenBackground()
}

#Preview("Sombre") {
    VStack(alignment: .leading, spacing: Spacing.m) {
        ForEach(GiftStatus.allCases, id: \.self) { StatusBadge(status: $0) }
    }
    .padding(Spacing.xl)
    .fcScreenBackground()
    .preferredColorScheme(.dark)
}
