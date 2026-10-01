import SwiftUI

/// Avatar rond pastel avec émoji ou initiale.
///
/// `colorName` : `pastelPink`, `pastelMint`, `pastelBlue`, `pastelPeach` ou
/// `pastelLavender`. Absent ou inconnu : pastel stable dérivé du prénom.
struct ChildAvatar: View {
    let name: String
    let emoji: String?
    let colorName: String?
    var size: CGFloat = 44

    var body: some View {
        Circle()
            .fill(Color.Theme.pastel(named: colorName) ?? Color.Theme.pastel(for: name))
            .overlay {
                if let emoji, !emoji.isEmpty {
                    Text(emoji)
                        .font(.system(size: size * 0.52))
                } else {
                    Text(Self.initial(of: name))
                        .font(.system(size: size * 0.42, weight: .bold, design: .rounded))
                        .foregroundStyle(Color.Theme.textPrimary)
                }
            }
            .frame(width: size, height: size)
            .accessibilityElement(children: .ignore)
            .accessibilityLabel(name)
    }

    static func initial(of name: String) -> String {
        name.trimmingCharacters(in: .whitespacesAndNewlines).first.map { String($0).uppercased() } ?? "?"
    }
}

/// Avatars qui se chevauchent, suivis d'un compteur « +N ».
struct AvatarStack: View {
    let names: [String]
    var maxVisible: Int = 5
    var size: CGFloat = 32

    private var visible: [String] { Array(names.prefix(max(0, maxVisible))) }
    private var overflow: Int { max(0, names.count - visible.count) }
    private var ringWidth: CGFloat { max(1.5, size * 0.06) }

    var body: some View {
        HStack(spacing: -size * 0.28) {
            ForEach(Array(visible.enumerated()), id: \.offset) { _, name in
                ChildAvatar(name: name, emoji: nil, colorName: nil, size: size)
                    .overlay { Circle().strokeBorder(Color.Theme.surface, lineWidth: ringWidth) }
            }
            if overflow > 0 {
                Text("+\(overflow)")
                    .font(.system(size: size * 0.36, weight: .semibold))
                    .monospacedDigit()
                    .foregroundStyle(Color.Theme.textSecondary)
                    .frame(width: size, height: size)
                    .background(Color.Theme.background, in: Circle())
                    .overlay { Circle().strokeBorder(Color.Theme.surface, lineWidth: ringWidth) }
            }
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(accessibilityText)
    }

    private var accessibilityText: String {
        guard !names.isEmpty else { return "Aucun participant" }
        let listed = visible.joined(separator: ", ")
        return overflow > 0 ? "\(listed) et \(overflow) autres" : listed
    }
}

private struct AvatarsPreview: View {
    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.l) {
            HStack(spacing: Spacing.m) {
                ChildAvatar(name: "Léo", emoji: "🦊", colorName: "pastelPeach", size: 56)
                ChildAvatar(name: "Emma", emoji: nil, colorName: "pastelPink", size: 56)
                ChildAvatar(name: "Noé", emoji: nil, colorName: nil)
                ChildAvatar(name: "", emoji: nil, colorName: "pastelLavender")
            }
            AvatarStack(names: ["Jonathan", "Claire", "Paul", "Mamie", "Papi", "Léo", "Emma", "Noé"])
            AvatarStack(names: ["Léo", "Emma", "Noé"], size: 24)
        }
        .padding(Spacing.xl)
        .fcCard()
        .padding(Spacing.xl)
        .fcScreenBackground()
    }
}

#Preview("Clair") {
    AvatarsPreview()
}

#Preview("Sombre") {
    AvatarsPreview().preferredColorScheme(.dark)
}
