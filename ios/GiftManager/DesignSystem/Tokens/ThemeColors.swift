import SwiftUI

/// Palette « Gift Manager ».
///
/// Chaque couleur est un color set de `Assets.xcassets/Theme/` avec une variante
/// sombre. Les paires texte / fond sont vérifiées AA (≥ 4,5:1) dans les deux
/// apparences — voir `DESIGN.md`. Ne jamais utiliser d'hexadécimal en dur dans
/// les écrans : passer par `Color.Theme`.
extension Color {
    enum Theme {
        // MARK: Surfaces

        /// Fond d'écran crème (sombre : bleu nuit profond).
        static let background = named("background")
        /// Cartes, champs, feuilles.
        static let surface = named("surface")
        /// Filets, bordures de champs, séparateurs.
        static let separator = named("separator")

        // MARK: Actions

        /// Bleu nuit des CTA principaux, FAB +, onglet actif.
        static let primary = named("primary")
        /// Texte et icônes posés sur `primary` ou `secondary`.
        static let onPrimary = named("onPrimary")
        /// Vert du CTA secondaire (« Ajouter à la liste » sur la fiche cadeau).
        static let secondary = named("secondary")

        // MARK: Texte

        static let textPrimary = named("textPrimary")
        static let textSecondary = named("textSecondary")

        // MARK: Accents

        /// Cœur « très envie ».
        static let heart = named("heart")
        /// Accent chaud (pastille « Surprise », alertes douces).
        static let accentAmber = named("accentAmber")

        // MARK: Statuts de cadeau (fond / texte)

        static let availableBg = named("availableBg")
        static let availableFg = named("availableFg")
        static let takenBg = named("takenBg")
        static let takenFg = named("takenFg")
        static let mineBg = named("mineBg")
        static let mineFg = named("mineFg")
        static let ownedBg = named("ownedBg")
        static let ownedFg = named("ownedFg")
        /// Cagnotte : miel doux, texte brun ambré (5,8:1 clair, 7,8:1 sombre).
        static let potBg = named("potBg")
        static let potFg = named("potFg")

        // MARK: Pastels (catégories, avatars, pastilles)

        static let pastelPink = named("pastelPink")
        static let pastelMint = named("pastelMint")
        static let pastelBlue = named("pastelBlue")
        static let pastelPeach = named("pastelPeach")
        static let pastelLavender = named("pastelLavender")

        /// Pastels dans un ordre stable, pour attribuer une couleur par défaut.
        static let pastels: [Color] = [pastelPink, pastelMint, pastelBlue, pastelPeach, pastelLavender]

        /// Résout un nom de pastel (`"pastelMint"`, `"mint"`…) ; `nil` si inconnu.
        static func pastel(named name: String?) -> Color? {
            guard let raw = name?.trimmingCharacters(in: .whitespaces).lowercased(), !raw.isEmpty else {
                return nil
            }
            switch raw.hasPrefix("pastel") ? String(raw.dropFirst("pastel".count)) : raw {
            case "pink": return pastelPink
            case "mint": return pastelMint
            case "blue": return pastelBlue
            case "peach": return pastelPeach
            case "lavender": return pastelLavender
            default: return nil
            }
        }

        /// Pastel déterministe dérivé d'une chaîne (même prénom → même couleur).
        static func pastel(for seed: String) -> Color {
            let sum = seed.unicodeScalars.reduce(0) { ($0 &* 31 &+ Int($1.value)) & 0x7FFF_FFFF }
            return pastels[sum % pastels.count]
        }

        private static func named(_ name: String) -> Color {
            Color("Theme/\(name)", bundle: .main)
        }
    }
}
