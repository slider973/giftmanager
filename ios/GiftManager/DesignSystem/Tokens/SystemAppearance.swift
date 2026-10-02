import SwiftUI
import UIKit

/// Habillage des barres système (UIKit) aux couleurs et à la typographie de la marque.
///
/// SwiftUI n'expose pas la police des titres de navigation : on passe par les proxys
/// `appearance()`, une seule fois au lancement (`FCSystemAppearance.apply()` dans `App`).
/// Les titres reprennent SF Pro Rounded, comme `Font.Theme.largeTitle` / `title`.
enum FCSystemAppearance {
    static func apply() {
        let textPrimary = UIColor(named: "Theme/textPrimary") ?? .label

        let navigationBar = UINavigationBar.appearance()
        navigationBar.largeTitleTextAttributes = [
            .font: roundedFont(.largeTitle, size: 34, weight: .bold),
            .foregroundColor: textPrimary,
        ]
        navigationBar.titleTextAttributes = [
            .font: roundedFont(.headline, size: 17, weight: .semibold),
            .foregroundColor: textPrimary,
        ]
    }

    /// Police arrondie mise à l'échelle Dynamic Type du style donné.
    private static func roundedFont(_ style: UIFont.TextStyle, size: CGFloat, weight: UIFont.Weight) -> UIFont {
        let system = UIFont.systemFont(ofSize: size, weight: weight)
        let descriptor = system.fontDescriptor.withDesign(.rounded) ?? system.fontDescriptor
        return UIFontMetrics(forTextStyle: style).scaledFont(for: UIFont(descriptor: descriptor, size: size))
    }
}
