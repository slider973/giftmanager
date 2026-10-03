#if DEBUG
import SwiftUI

/// Galerie de revue du design system (DEBUG uniquement).
/// Montre tokens et composants dans leurs différents états.
struct DesignSystemGallery: View {
    @State private var tab = 0
    @State private var url = ""
    @State private var name = "Léo"
    @State private var isFavorite = true

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: Spacing.xxl) {
                header
                loadingSection
                colorsSection
                typographySection
                giftCardsSection
                eventsSection
                storeLinksSection
                buttonsSection
                controlsSection
                avatarsSection
                featuresSection
                emptyStateSection
            }
            .padding(.horizontal, Spacing.xl)
            .padding(.vertical, Spacing.l)
            .containerRelativeFrame(.horizontal)
        }
        .fcScreenBackground()
    }

    // MARK: Sections

    private var header: some View {
        VStack(alignment: .leading, spacing: Spacing.xs) {
            Text("Gift Manager")
                .font(Font.Theme.largeTitle)
                .foregroundStyle(Color.Theme.textPrimary)
            Text("Design system · galerie de revue")
                .font(Font.Theme.body)
                .foregroundStyle(Color.Theme.textSecondary)
        }
    }

    private var loadingSection: some View {
        GallerySection(title: "Chargement") {
            HStack(spacing: Spacing.xl) {
                GiftLoadingView(size: 72)
                GiftLoadingView(size: 44, label: "Chargement…")
            }
            .frame(maxWidth: .infinity)
        }
    }

    private var colorsSection: some View {
        GallerySection(title: "Couleurs") {
            LazyVGrid(columns: [GridItem(.adaptive(minimum: 92), spacing: Spacing.s)], spacing: Spacing.s) {
                ForEach(Self.swatches, id: \.name) { swatch in
                    VStack(alignment: .leading, spacing: Spacing.xs) {
                        RoundedRectangle(cornerRadius: Radius.thumb / 2, style: .continuous)
                            .fill(swatch.color)
                            .frame(height: 44)
                            .overlay {
                                RoundedRectangle(cornerRadius: Radius.thumb / 2, style: .continuous)
                                    .strokeBorder(Color.Theme.separator)
                            }
                        Text(swatch.name)
                            .font(Font.Theme.caption)
                            .foregroundStyle(Color.Theme.textSecondary)
                            .lineLimit(1)
                            .minimumScaleFactor(0.8)
                    }
                }
            }
        }
    }

    private var typographySection: some View {
        GallerySection(title: "Typographie") {
            VStack(alignment: .leading, spacing: Spacing.s) {
                Text("Notre famille").font(Font.Theme.largeTitle)
                Text("Bienvenue sur Gift Manager").font(Font.Theme.title)
                Text("LEGO Technic McLaren F1").font(Font.Theme.headline)
                Text("Organisez les cadeaux de toute la famille en toute simplicité.").font(Font.Theme.body)
                Text("Liste (8) · Possède déjà (3)").font(Font.Theme.callout)
                Text("8 ans · Ses envies").font(Font.Theme.caption)
                Text("Disponible").font(Font.Theme.captionBold)
            }
            .foregroundStyle(Color.Theme.textPrimary)
        }
    }

    private var giftCardsSection: some View {
        GallerySection(title: "Cartes cadeau") {
            VStack(spacing: Spacing.m) {
                GiftCard(title: "LEGO Technic McLaren F1", imageURL: nil, priceText: "CHF 199.–",
                         storeText: "Galaxus", countryCode: "CH", isFavorite: true, status: .available)
                GiftCard(title: "PlayStation 5 Pro", imageURL: nil, priceText: "CHF 799.–",
                         storeText: "Digitec", countryCode: "CH", isFavorite: false, status: .taken)
                GiftCard(title: "Casque Sony WH-1000XM5", imageURL: URL(string: "https://invalid.invalid/x.png"),
                         priceText: "€ 299,00", storeText: "Amazon.fr", countryCode: "FR",
                         isFavorite: true, status: .mine)
                GiftCard(title: "Nintendo Switch OLED", imageURL: nil, priceText: "CHF 349.–",
                         storeText: "Fnac", countryCode: "CH", isFavorite: false, status: .owned)
                GiftCard(title: "Maillot PSG 2025 (vue parent, mode surprise)", imageURL: nil,
                         priceText: "CHF 89.–", storeText: "Nike", countryCode: "CH",
                         isFavorite: true, status: nil)
            }
        }
    }

    private var eventsSection: some View {
        VStack(alignment: .leading, spacing: Spacing.s) {
            SectionHeader(title: "Événements à venir", actionSystemImage: "plus", action: {},
                          actionLabel: "Ajouter un événement")
            VStack(spacing: Spacing.m) {
                EventRow(title: "Anniversaire de Léo", dateText: "12 mars 2026", subtitle: "8 ans",
                         kind: .birthday, avatarNames: ["Claire", "Paul", "Mamie", "Papi"])
                EventRow(title: "Noël 2026", dateText: "25 décembre 2026", subtitle: nil,
                         kind: .christmas, avatarNames: ["Léo", "Emma", "Noé"])
                EventRow(title: "Fête des grands-parents", dateText: "5 octobre 2026", subtitle: "Chez Mamie",
                         kind: .other, avatarNames: [])
            }
        }
    }

    private var storeLinksSection: some View {
        GallerySection(title: "Liens par pays") {
            VStack(spacing: Spacing.s) {
                StoreLinkRow(store: "Galaxus (CH)", countryCode: "CH", priceText: "CHF 199.–") {}
                StoreLinkRow(store: "Amazon.fr (FR)", countryCode: "FR", priceText: "€ 199,99") {}
                StoreLinkRow(store: "Boutique inconnue", countryCode: nil, priceText: nil) {}
            }
        }
    }

    private var buttonsSection: some View {
        GallerySection(title: "Boutons") {
            VStack(spacing: Spacing.m) {
                PrimaryButton(title: "Continuer", systemImage: "chevron.right") {}
                PrimaryButton(title: "Ajouter à la liste", isLoading: true) {}
                PrimaryButton(title: "Continuer") {}.disabled(true)
                SecondaryButton(title: "Ajouter à la liste", systemImage: "gift") {}
                TextLinkButton(title: "Modifier les informations") {}
                ShareLink(item: "Code : DEMO26") {
                    FCPillLabel(title: "Partager l'invitation", systemImage: "square.and.arrow.up")
                }
                .buttonStyle(FCPressableStyle())
            }
        }
    }

    private var controlsSection: some View {
        GallerySection(title: "Contrôles") {
            VStack(alignment: .leading, spacing: Spacing.l) {
                SegmentedTabs(selection: $tab, titles: ["Liste (8)", "Possède déjà (3)", "Idées (2)"])
                FCTextField(title: "Lien du cadeau", text: $url, systemImage: "magnifyingglass",
                            prompt: "https://www.galaxus.ch/…", isTitleHidden: true)
                FCTextField(title: "Prénom", text: $name, prompt: "Ex. Léo")
                ViewThatFits(in: .horizontal) {
                    HStack(spacing: Spacing.m) { badges }
                    VStack(alignment: .leading, spacing: Spacing.s) { badges }
                }
                HStack(spacing: Spacing.l) {
                    PriorityHeart(isOn: isFavorite) { isFavorite.toggle() }
                    PriorityHeart(isOn: false)
                }
                FCNotice(systemImage: "eye.slash", text: "Mode surprise : tu ne vois pas ce qui a été réservé.",
                         tone: .surprise)
                FCNotice(systemImage: "archivebox", text: "Événement passé : fiche archivée.")
                ViewThatFits(in: .horizontal) {
                    HStack(spacing: Spacing.m) { flags }
                    VStack(alignment: .leading, spacing: Spacing.s) { flags }
                }
                .font(.title3)
            }
        }
    }

    @ViewBuilder private var badges: some View {
        ForEach(GiftStatus.allCases, id: \.self) { StatusBadge(status: $0) }
    }

    @ViewBuilder private var flags: some View {
        ForEach(["CH", "FR", "DE", "IT", "BE", "??"], id: \.self) { CountryFlag(code: $0) }
    }

    private var avatarsSection: some View {
        GallerySection(title: "Avatars") {
            VStack(alignment: .leading, spacing: Spacing.l) {
                HStack(spacing: Spacing.m) {
                    ChildAvatar(name: "Léo", emoji: "🦊", colorName: "pastelPeach", size: 56)
                    ChildAvatar(name: "Emma", emoji: nil, colorName: "pastelPink", size: 56)
                    ChildAvatar(name: "Noé", emoji: "🐻", colorName: "pastelMint", size: 56)
                    ChildAvatar(name: "Lou", emoji: nil, colorName: "pastelLavender", size: 56)
                }
                AvatarStack(names: ["Jonathan", "Claire", "Paul", "Mamie", "Papi", "Léo", "Emma", "Noé",
                                    "Lou", "Tom", "Zoé", "Max", "Inès", "Hugo"])
                Text("12 membres · 3 foyers")
                    .font(Font.Theme.caption)
                    .foregroundStyle(Color.Theme.textSecondary)
            }
        }
    }

    private var featuresSection: some View {
        GallerySection(title: "Pastilles") {
            LazyVGrid(columns: [GridItem(.flexible(), alignment: .top), GridItem(.flexible(), alignment: .top)],
                      spacing: Spacing.xl) {
                FeaturePill(systemImage: "person.2.fill", color: Color.Theme.heart,
                            title: "Groupes familiaux", subtitle: "Plusieurs foyers, plusieurs pays",
                            background: Color.Theme.pastelPink)
                FeaturePill(systemImage: "gift.fill", color: Color.Theme.availableFg,
                            title: "Listes par événement", subtitle: "Anniversaire, Noël, et plus",
                            background: Color.Theme.pastelMint)
                FeaturePill(systemImage: "link", color: Color.Theme.mineFg,
                            title: "Toutes les boutiques", subtitle: "Amazon, Galaxus, Fnac, etc.",
                            background: Color.Theme.pastelBlue)
                FeaturePill(systemImage: "lock.fill", color: Color.Theme.accentAmber,
                            title: "Surprise garantie", subtitle: "« Déjà pris » sans savoir par qui",
                            background: Color.Theme.pastelPeach)
            }
        }
    }

    private var emptyStateSection: some View {
        GallerySection(title: "État vide") {
            EmptyStateView(imageName: "empty_box", title: "Aucun cadeau pour l'instant",
                           message: "Ajoutez une première envie depuis n'importe quelle boutique.",
                           actionTitle: "Ajouter un cadeau") {}
                .fcCard()
        }
    }

    // MARK: Données

    private struct Swatch {
        let name: String
        let color: Color
    }

    private static let swatches: [Swatch] = [
        Swatch(name: "background", color: Color.Theme.background),
        Swatch(name: "surface", color: Color.Theme.surface),
        Swatch(name: "primary", color: Color.Theme.primary),
        Swatch(name: "secondary", color: Color.Theme.secondary),
        Swatch(name: "textPrimary", color: Color.Theme.textPrimary),
        Swatch(name: "textSecondary", color: Color.Theme.textSecondary),
        Swatch(name: "separator", color: Color.Theme.separator),
        Swatch(name: "heart", color: Color.Theme.heart),
        Swatch(name: "accentAmber", color: Color.Theme.accentAmber),
        Swatch(name: "pastelPink", color: Color.Theme.pastelPink),
        Swatch(name: "pastelMint", color: Color.Theme.pastelMint),
        Swatch(name: "pastelBlue", color: Color.Theme.pastelBlue),
        Swatch(name: "pastelPeach", color: Color.Theme.pastelPeach),
        Swatch(name: "pastelLavender", color: Color.Theme.pastelLavender),
    ]
}

/// Titre de section + contenu, pour la galerie.
private struct GallerySection<Content: View>: View {
    let title: String
    @ViewBuilder let content: Content

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.m) {
            Text(title)
                .font(Font.Theme.headline)
                .foregroundStyle(Color.Theme.textPrimary)
                .accessibilityAddTraits(.isHeader)
            content
        }
    }
}

#Preview("Clair") {
    DesignSystemGallery()
}

#Preview("Sombre") {
    DesignSystemGallery().preferredColorScheme(.dark)
}
#endif
