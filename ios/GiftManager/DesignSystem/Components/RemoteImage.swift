import SwiftUI

/// Image distante avec placeholder doux (cadeau sur fond pastel).
///
/// Remplit le cadre fourni par l'appelant et s'y découpe : poser `.frame` puis
/// `.clipShape` à l'extérieur. Trois états : chargement (cadeau qui respire),
/// succès (fondu), échec ou URL absente (cadeau + pastille discrète).
struct RemoteImage: View {
    let url: URL?
    var contentMode: ContentMode = .fill

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
                            RemoteImagePlaceholder(state: .failed)
                        case .empty:
                            RemoteImagePlaceholder(state: .loading)
                        @unknown default:
                            RemoteImagePlaceholder(state: .missing)
                        }
                    }
                } else {
                    RemoteImagePlaceholder(state: .missing)
                }
            }
            .clipped()
    }
}

private struct RemoteImagePlaceholder: View {
    enum Phase { case loading, missing, failed }
    let state: Phase

    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        GeometryReader { proxy in
            let side = min(proxy.size.width, proxy.size.height)
            ZStack {
                Color.Theme.pastelPeach
                Image(systemName: "gift.fill")
                    .font(.system(size: max(14, side * 0.32), weight: .regular))
                    .foregroundStyle(Color.Theme.accentAmber.opacity(state == .failed ? 0.45 : 0.75))
                    .phaseAnimator(state == .loading && !reduceMotion ? [1.0, 0.55] : [1.0]) { icon, opacity in
                        icon.opacity(opacity)
                    } animation: { _ in .easeInOut(duration: 0.9) }

                if state == .failed {
                    Image(systemName: "exclamationmark.circle.fill")
                        .font(.system(size: max(10, side * 0.16), weight: .semibold))
                        .foregroundStyle(Color.Theme.textSecondary)
                        .padding(side * 0.08)
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
