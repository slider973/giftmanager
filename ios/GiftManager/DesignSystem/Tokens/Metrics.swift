import SwiftUI

/// Grille d'espacement sur 4 pt. Marge d'écran : `xl` ; entre cartes : `m`.
enum Spacing {
    static let xs: CGFloat = 4
    static let s: CGFloat = 8
    static let m: CGFloat = 12
    static let l: CGFloat = 16
    static let xl: CGFloat = 20
    static let xxl: CGFloat = 32
}

/// Rayons d'angle (toujours en style `.continuous`). Boutons et badges : `Capsule`.
enum Radius {
    static let card: CGFloat = 20
    static let thumb: CGFloat = 14
    static let field: CGFloat = 14
}

/// Tailles minimales d'interaction.
enum HitTarget {
    /// Cible tactile minimale (Apple HIG).
    static let minimum: CGFloat = 44
    /// Hauteur des boutons pilule pleine largeur.
    static let button: CGFloat = 52
}
