import SwiftUI

enum EventKind: Equatable, CaseIterable {
    case christmas
    case birthday
    case other

    var tile: Color {
        switch self {
        case .christmas: Color.Theme.pastelMint
        case .birthday: Color.Theme.pastelPink
        case .other: Color.Theme.pastelPeach
        }
    }

    var accessibilityName: String {
        switch self {
        case .christmas: "Noël"
        case .birthday: "Anniversaire"
        case .other: "Événement"
        }
    }
}

/// Carte d'événement (écran 2) : vignette illustrée, titre, date · sous-titre,
/// participants, chevron. À envelopper dans un `NavigationLink`.
struct EventRow: View {
    let title: String
    let dateText: String
    let subtitle: String?
    let kind: EventKind
    let avatarNames: [String]

    @ScaledMetric(relativeTo: .headline) private var tileSize: CGFloat = 56

    var body: some View {
        HStack(spacing: Spacing.m) {
            EventKindTile(kind: kind, size: tileSize)

            VStack(alignment: .leading, spacing: Spacing.xs) {
                Text(title)
                    .font(Font.Theme.headline)
                    .foregroundStyle(Color.Theme.textPrimary)

                Text(metaLine)
                    .font(Font.Theme.caption)
                    .foregroundStyle(Color.Theme.textSecondary)

                if !avatarNames.isEmpty {
                    AvatarStack(names: avatarNames, maxVisible: 3, size: 24)
                        .padding(.top, Spacing.xs / 2)
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)

            Image(systemName: "chevron.right")
                .font(Font.Theme.callout.weight(.semibold))
                .foregroundStyle(Color.Theme.textSecondary)
                .accessibilityHidden(true)
        }
        .fcCard(padding: Spacing.m)
        .accessibilityElement(children: .combine)
    }

    private var metaLine: String {
        [dateText, subtitle].compactMap { $0 }.filter { !$0.isEmpty }.joined(separator: " · ")
    }
}

/// Vignette carrée arrondie illustrant le type d'événement.
struct EventKindTile: View {
    let kind: EventKind
    var size: CGFloat = 56

    var body: some View {
        RoundedRectangle(cornerRadius: Radius.thumb, style: .continuous)
            .fill(kind.tile)
            .frame(width: size, height: size)
            .overlay {
                switch kind {
                case .christmas:
                    Text("🎄").font(.system(size: size * 0.5))
                case .birthday:
                    Text("🎂").font(.system(size: size * 0.5))
                case .other:
                    Image("gift_red")
                        .resizable()
                        .scaledToFit()
                        .padding(size * 0.18)
                }
            }
            .accessibilityElement(children: .ignore)
            .accessibilityLabel(kind.accessibilityName)
    }
}

private struct EventRowPreview: View {
    var body: some View {
        VStack(spacing: Spacing.m) {
            EventRow(title: "Anniversaire de Léo", dateText: "12 mars 2026", subtitle: "8 ans",
                     kind: .birthday, avatarNames: ["Claire", "Paul", "Mamie"])
            EventRow(title: "Noël 2026", dateText: "25 décembre 2026", subtitle: nil,
                     kind: .christmas, avatarNames: ["Léo", "Emma", "Noé", "Papi", "Mamie"])
            EventRow(title: "Fête de fin d'année", dateText: "3 juin 2026", subtitle: "Chez Mamie",
                     kind: .other, avatarNames: [])
        }
        .padding(Spacing.xl)
        .fcScreenBackground()
    }
}

#Preview("Clair") {
    EventRowPreview()
}

#Preview("Sombre") {
    EventRowPreview().preferredColorScheme(.dark)
}
