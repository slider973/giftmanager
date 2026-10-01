import SwiftUI

/// Champ arrondi blanc avec libellé visible au-dessus (jamais de placeholder seul).
///
/// `isTitleHidden` masque le libellé à l'écran (champ de recherche d'URL de la
/// maquette) tout en le conservant pour VoiceOver.
struct FCTextField: View {
    let title: String
    @Binding var text: String
    var systemImage: String? = nil
    var prompt: String? = nil
    var isTitleHidden: Bool = false

    @FocusState private var isFocused: Bool
    @ScaledMetric(relativeTo: .body) private var minHeight: CGFloat = 48

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.s) {
            if !isTitleHidden {
                Text(title)
                    .font(Font.Theme.captionBold)
                    .foregroundStyle(Color.Theme.textSecondary)
                    .accessibilityHidden(true)
            }

            HStack(spacing: Spacing.s) {
                if let systemImage {
                    Image(systemName: systemImage)
                        .font(Font.Theme.callout)
                        .foregroundStyle(Color.Theme.textSecondary)
                        .accessibilityHidden(true)
                }
                TextField(
                    title,
                    text: $text,
                    prompt: prompt.map { Text($0).foregroundStyle(Color.Theme.textSecondary) }
                )
                .font(Font.Theme.body)
                .foregroundStyle(Color.Theme.textPrimary)
                .tint(Color.Theme.primary)
                .focused($isFocused)

                if isFocused, !text.isEmpty {
                    Button {
                        text = ""
                    } label: {
                        Image(systemName: "xmark.circle.fill")
                            .foregroundStyle(Color.Theme.textSecondary)
                            .frame(width: HitTarget.minimum, height: HitTarget.minimum)
                            .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                    .padding(.trailing, -Spacing.m)
                    .accessibilityLabel("Effacer")
                }
            }
            .padding(.horizontal, Spacing.l)
            .frame(minHeight: minHeight)
            .background(Color.Theme.surface, in: RoundedRectangle(cornerRadius: Radius.field, style: .continuous))
            .overlay {
                RoundedRectangle(cornerRadius: Radius.field, style: .continuous)
                    .strokeBorder(isFocused ? Color.Theme.primary : Color.Theme.separator,
                                  lineWidth: isFocused ? 1.5 : 1)
            }
            .contentShape(Rectangle())
            .onTapGesture { isFocused = true }
            .animation(.easeOut(duration: 0.15), value: isFocused)
        }
        .accessibilityElement(children: .contain)
    }
}

private struct FCTextFieldPreview: View {
    @State private var url = "https://www.galaxus.ch/fr/s1/product/lego-technic"
    @State private var name = ""

    var body: some View {
        VStack(spacing: Spacing.l) {
            FCTextField(title: "Lien du cadeau", text: $url, systemImage: "magnifyingglass",
                        prompt: "Coller un lien", isTitleHidden: true)
            FCTextField(title: "Prénom", text: $name, prompt: "Ex. Léo")
        }
        .padding(Spacing.xl)
        .fcScreenBackground()
    }
}

#Preview("Clair") {
    FCTextFieldPreview()
}

#Preview("Sombre") {
    FCTextFieldPreview().preferredColorScheme(.dark)
}
