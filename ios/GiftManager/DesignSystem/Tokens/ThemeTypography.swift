import SwiftUI

/// Échelle typographique. Toutes les polices s'appuient sur les styles de texte
/// système : elles suivent Dynamic Type sans taille codée en dur.
///
/// Les deux niveaux de titre utilisent SF Pro Rounded : c'est la touche chaleureuse
/// de la marque. Le reste reste en SF Pro, pour la lisibilité.
extension Font {
    enum Theme {
        /// 34 pt Bold arrondi — « Notre famille », « Gift Manager ».
        static let largeTitle = Font.system(.largeTitle, design: .rounded, weight: .bold)
        /// 22 pt Bold arrondi — titres d'écran secondaires, prénom de l'enfant.
        static let title = Font.system(.title2, design: .rounded, weight: .bold)
        /// 17 pt Semibold — nom d'un cadeau, d'un événement, boutons.
        static let headline = Font.system(.headline, design: .default, weight: .semibold)
        /// 15 pt Regular — textes courants.
        static let body = Font.system(.subheadline, design: .default, weight: .regular)
        /// 16 pt Regular — libellés d'onglets, liens texte, lignes de liste.
        static let callout = Font.system(.callout, design: .default, weight: .regular)
        /// 12 pt Regular — prix, boutique, métadonnées.
        static let caption = Font.system(.caption, design: .default, weight: .regular)
        /// 12 pt Semibold — badges, libellés de champs, compteurs.
        static let captionBold = Font.system(.caption, design: .default, weight: .semibold)
    }
}
