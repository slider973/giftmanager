import SwiftUI

/// Encart d'information discret : icône + phrase, sur fond teinté.
///
/// - `.surprise` : lavande (« Enfants protégés » de la maquette) pour rappeler le mode surprise.
/// - `.neutral` : carte claire bordée, pour les états d'archive et les précisions.
/// - `.warning` : rose « Déjà pris », pour une alerte douce (cadeau déjà possédé…).
///
/// Paires couleur / fond AA dans les deux apparences (mêmes tokens que les badges de statut).
struct FCNotice: View {
    enum Tone: Equatable { case surprise, neutral, warning }

    let systemImage: String
    let text: String
    var tone: Tone = .neutral

    var body: some View {
        HStack(alignment: .firstTextBaseline, spacing: Spacing.s) {
            Image(systemName: systemImage)
                .font(Font.Theme.caption.weight(.semibold))
                .accessibilityHidden(true)
            Text(text)
                .font(Font.Theme.caption)
                .fixedSize(horizontal: false, vertical: true)
                .frame(maxWidth: .infinity, alignment: .leading)
        }
        .foregroundStyle(foreground)
        .padding(.horizontal, Spacing.m)
        .padding(.vertical, Spacing.s + 2)
        .background(background, in: RoundedRectangle(cornerRadius: Radius.field, style: .continuous))
        .overlay {
            if tone == .neutral {
                RoundedRectangle(cornerRadius: Radius.field, style: .continuous)
                    .strokeBorder(Color.Theme.separator, lineWidth: 1)
            }
        }
        .accessibilityElement(children: .combine)
    }

    private var foreground: Color {
        switch tone {
        case .surprise: Color.Theme.ownedFg
        case .neutral: Color.Theme.textSecondary
        case .warning: Color.Theme.takenFg
        }
    }

    private var background: Color {
        switch tone {
        case .surprise: Color.Theme.ownedBg
        case .neutral: Color.Theme.surface
        case .warning: Color.Theme.takenBg
        }
    }
}

private struct FCNoticePreview: View {
    var body: some View {
        VStack(spacing: Spacing.m) {
            FCNotice(systemImage: "eye.slash",
                     text: "Mode surprise : tu ne vois pas ce qui a été réservé pour tes enfants.",
                     tone: .surprise)
            FCNotice(systemImage: "archivebox", text: "Événement passé : les listes sont archivées en lecture seule.")
            FCNotice(systemImage: "exclamationmark.triangle", text: "Les parents l'ont noté comme déjà possédé.",
                     tone: .warning)
        }
        .padding(Spacing.xl)
        .fcScreenBackground()
    }
}

#Preview("Clair") {
    FCNoticePreview()
}

#Preview("Sombre") {
    FCNoticePreview().preferredColorScheme(.dark)
}
