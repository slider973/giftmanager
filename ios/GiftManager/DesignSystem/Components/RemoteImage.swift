import SwiftUI

/// Image distante avec placeholder doux (cadeau sur fond pastel).
///
/// Remplit le cadre fourni par l'appelant et s'y découpe : poser `.frame` puis
/// `.clipShape` à l'extérieur. Trois états : chargement (cadeau qui respire),
/// succès (fondu), échec ou URL absente (cadeau + pastille discrète).
///
/// `placeholderSeed` (ex. le titre du cadeau) choisit un pastel stable pour le
/// placeholder : une liste sans photos alterne les teintes au lieu d'aligner des
/// blocs identiques. Sans graine : pêche.
struct RemoteImage: View {
    let url: URL?
    var contentMode: ContentMode = .fill
    var placeholderSeed: String? = nil

    var body: some View {
        Color.clear
            .overlay {
                if let url {
                    AsyncImage(url: url, transaction: Transaction(animation: .easeOut(duration: 0.25))) { phase in
                        switch phase {
                        case .success(let image):
                            image
                                .resizable()
                                .aspectRatio(contentMode: contentMode)
                                .transition(.opacity)
                        case .failure:
                            RemoteImagePlaceholder(state: .failed, seed: placeholderSeed)
                        case .empty:
                            RemoteImagePlaceholder(state: .loading, seed: placeholderSeed)
                        @unknown default:
                            RemoteImagePlaceholder(state: .missing, seed: placeholderSeed)
                        }
                    }
                } else {
                    RemoteImagePlaceholder(state: .missing, seed: placeholderSeed)
                }
            }
            .clipped()
    }
}

private struct RemoteImagePlaceholder: View {
    enum Phase { case loading, missing, failed }
    let state: Phase
    var seed: String? = nil

    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    private var fill: Color {
        seed.map { Color.Theme.pastel(for: $0) } ?? Color.Theme.pastelPeach
    }

    var body: some View {
        GeometryReader { proxy in
            let side = min(proxy.size.width, proxy.size.height)
            // Le symbole reste discret : un tiers de la vignette, plafonné pour les
            // grands formats (fiche cadeau) où il deviendrait un pictogramme géant.
            let glyph = min(max(14, side * 0.34), 48)
            ZStack {
                fill
                Image(systemName: "gift.fill")
                    .font(.system(size: glyph, weight: .regular))
                    .foregroundStyle(Color.Theme.textPrimary.opacity(state == .failed ? 0.18 : 0.28))
                    .phaseAnimator(state == .loading && !reduceMotion ? [1.0, 0.5] : [1.0]) { icon, opacity in
                        icon.opacity(opacity)
                    } animation: { _ in .easeInOut(duration: 0.9) }

                if state == .failed {
                    Image(systemName: "exclamationmark.circle.fill")
                        .font(.system(size: max(10, glyph * 0.45), weight: .semibold))
                        .foregroundStyle(Color.Theme.textSecondary)
                        .padding(max(4, side * 0.06))
                        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .bottomTrailing)
                }
            }
            .frame(width: proxy.size.width, height: proxy.size.height)
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(accessibilityText)
    }

    private var accessibilityText: String {
        switch state {
        case .loading: "Image en cours de chargement"
        case .missing: "Pas d'image"
        case .failed: "Image indisponible"
        }
    }
}

private struct RemoteImagePreviewGrid: View {
    var body: some View {
        HStack(spacing: Spacing.m) {
            // Chargement : URL qui ne répond jamais en preview.
            RemoteImage(url: URL(string: "https://10.255.255.1/image.jpg"))
            RemoteImage(url: nil)
            RemoteImage(url: nil, placeholderSeed: "LEGO Technic")
            RemoteImage(url: URL(string: "https://invalid.invalid/x.png"))
        }
        .frame(height: 88)
        .clipShape(RoundedRectangle(cornerRadius: Radius.thumb, style: .continuous))
        .padding(Spacing.xl)
        .fcScreenBackground()
    }
}

#Preview("Clair") {
    RemoteImagePreviewGrid()
}

#Preview("Sombre") {
    RemoteImagePreviewGrid().preferredColorScheme(.dark)
}
