import SwiftUI

// MARK: - Pilule pleine largeur (base commune)

/// Les symboles directionnels (`chevron.*`, `arrow.*`) se placent après le libellé
/// (« Continuer › ») ; les autres avant (« 🎁 Ajouter à la liste »).
private struct PillButton: View {
    let title: String
    let systemImage: String?
    let isLoading: Bool
    let fill: Color
    let action: () -> Void

    @Environment(\.isEnabled) private var isEnabled

    private var isTrailingImage: Bool {
        guard let systemImage else { return false }
        return systemImage.hasPrefix("chevron") || systemImage.hasPrefix("arrow")
    }

    var body: some View {
        Button(action: action) {
            ZStack {
                label.opacity(isLoading ? 0 : 1)
                if isLoading {
                    ProgressView()
                        .tint(Color.Theme.onPrimary)
                }
            }
            .font(Font.Theme.headline)
            .foregroundStyle(Color.Theme.onPrimary)
            .frame(maxWidth: .infinity, minHeight: HitTarget.button)
            .padding(.horizontal, Spacing.xl)
            .background(fill, in: Capsule())
            .opacity(isEnabled ? 1 : 0.45)
            .contentShape(Capsule())
        }
        .buttonStyle(FCPressableStyle())
        .disabled(isLoading)
        .accessibilityLabel(title)
        .accessibilityValue(isLoading ? "Chargement en cours" : "")
    }

    /// Libellé centré ; un chevron se cale contre le bord droit, comme sur la maquette.
    private var label: some View {
        HStack(spacing: Spacing.s) {
            if let systemImage, !isTrailingImage {
                Image(systemName: systemImage).accessibilityHidden(true)
            }
            Text(title)
                .multilineTextAlignment(.center)
        }
        .padding(.horizontal, isTrailingImage ? Spacing.xl : 0)
        .frame(maxWidth: .infinity)
        .overlay(alignment: .trailing) {
            if let systemImage, isTrailingImage {
                Image(systemName: systemImage)
                    .font(Font.Theme.callout.weight(.semibold))
                    .accessibilityHidden(true)
            }
        }
    }
}

// MARK: - Public

/// CTA principal bleu nuit (« Continuer », « Ajouter à la liste »). Un seul par écran.
struct PrimaryButton: View {
    let title: String
    var systemImage: String? = nil
    var isLoading: Bool = false
    let action: () -> Void

    var body: some View {
        PillButton(title: title, systemImage: systemImage, isLoading: isLoading,
                   fill: Color.Theme.primary, action: action)
    }
}

/// CTA vert (« Ajouter à la liste » sur la fiche cadeau).
struct SecondaryButton: View {
    let title: String
    var systemImage: String? = nil
    var isLoading: Bool = false
    let action: () -> Void

    var body: some View {
        PillButton(title: title, systemImage: systemImage, isLoading: isLoading,
                   fill: Color.Theme.secondary, action: action)
    }
}

/// Lien texte discret (« Modifier les informations »). Cible ≥ 44 pt.
struct TextLinkButton: View {
    let title: String
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Text(title)
                .font(Font.Theme.callout.weight(.medium))
                .foregroundStyle(Color.Theme.primary)
                .multilineTextAlignment(.center)
                .padding(.horizontal, Spacing.s)
                .frame(minHeight: HitTarget.minimum)
                .contentShape(Rectangle())
        }
        .buttonStyle(FCPressableStyle(pressedScale: 1))
    }
}

// MARK: - Previews

private struct ButtonsPreview: View {
    var body: some View {
        VStack(spacing: Spacing.m) {
            PrimaryButton(title: "Continuer", systemImage: "chevron.right") {}
            PrimaryButton(title: "Ajouter à la liste") {}
            PrimaryButton(title: "Ajouter à la liste", isLoading: true) {}
            PrimaryButton(title: "Continuer") {}.disabled(true)
            SecondaryButton(title: "Ajouter à la liste", systemImage: "gift") {}
            SecondaryButton(title: "Ajouter à la liste", systemImage: "gift", isLoading: true) {}
            TextLinkButton(title: "Modifier les informations") {}
        }
        .padding(Spacing.xl)
        .fcScreenBackground()
    }
}

#Preview("Clair") {
    ButtonsPreview()
}

#Preview("Sombre") {
    ButtonsPreview().preferredColorScheme(.dark)
}
