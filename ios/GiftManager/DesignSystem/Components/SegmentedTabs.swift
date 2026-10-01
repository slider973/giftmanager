import SwiftUI

/// Onglets soulignés (« Liste (8) | Possède déjà (3) | Idées (2) »).
///
/// Largeurs égales tant que les libellés tiennent ; aux grandes tailles de texte,
/// la rangée devient défilable horizontalement plutôt que de tronquer.
struct SegmentedTabs: View {
    @Binding var selection: Int
    let titles: [String]

    @Namespace private var underline
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        ViewThatFits(in: .horizontal) {
            row(equalWidth: true)
            ScrollView(.horizontal, showsIndicators: false) {
                row(equalWidth: false)
            }
        }
        .background(alignment: .bottom) {
            Rectangle()
                .fill(Color.Theme.separator)
                .frame(height: 1)
        }
    }

    private func row(equalWidth: Bool) -> some View {
        HStack(spacing: equalWidth ? 0 : Spacing.l) {
            ForEach(Array(titles.enumerated()), id: \.offset) { index, title in
                tab(title: title, index: index)
                    .frame(maxWidth: equalWidth ? .infinity : nil)
            }
        }
    }

    private func tab(title: String, index: Int) -> some View {
        let isSelected = index == selection
        return Button {
            withAnimation(reduceMotion ? nil : .spring(response: 0.3, dampingFraction: 0.85)) {
                selection = index
            }
        } label: {
            Text(title)
                .font(isSelected ? Font.Theme.callout.weight(.semibold) : Font.Theme.callout)
                .foregroundStyle(isSelected ? Color.Theme.textPrimary : Color.Theme.textSecondary)
                .lineLimit(1)
                .fixedSize()
                .padding(.horizontal, Spacing.xs)
                .frame(minHeight: HitTarget.minimum)
                .overlay(alignment: .bottom) {
                    if isSelected {
                        Capsule()
                            .fill(Color.Theme.primary)
                            .frame(height: 2.5)
                            .matchedGeometryEffect(id: "underline", in: underline)
                    }
                }
                .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(isSelected ? .isSelected : [])
        .accessibilityHint("Onglet \(index + 1) sur \(titles.count)")
    }
}

private struct SegmentedTabsPreview: View {
    @State private var selection = 0

    var body: some View {
        VStack(spacing: Spacing.xxl) {
            SegmentedTabs(selection: $selection, titles: ["Liste (8)", "Possède déjà (3)", "Idées (2)"])
            SegmentedTabs(selection: .constant(1), titles: ["Événements", "Membres", "Paramètres"])
        }
        .padding(Spacing.xl)
        .fcScreenBackground()
    }
}

#Preview("Clair") {
    SegmentedTabsPreview()
}

#Preview("Sombre") {
    SegmentedTabsPreview().preferredColorScheme(.dark)
}

#Preview("Texte XXXL") {
    SegmentedTabsPreview().dynamicTypeSize(.accessibility3)
}
