import Foundation

/// Détection des doublons dans la liste d'un enfant (#61).
///
/// Un cadeau rangé dans un autre événement n'apparaît pas dans la vue courante : sans
/// avertissement, l'utilisateur le croit perdu et le recrée. La comparaison est volontairement
/// tolérante (casse, accents, espaces, ponctuation) pour attraper les ressaisies manuelles,
/// et les URL sont réduites à leur partie significative pour ignorer les paramètres de suivi.
enum DuplicateMatch {
    /// Titre réduit à ses caractères significatifs, en minuscules et sans accents.
    static func normalize(_ title: String) -> String {
        title
            .folding(options: [.diacriticInsensitive, .caseInsensitive], locale: Locale(identifier: "fr_FR"))
            .components(separatedBy: CharacterSet.alphanumerics.inverted)
            .filter { !$0.isEmpty }
            .joined(separator: " ")
    }

    /// Deux liens désignent le même article : hôte et chemin identiques, paramètres ignorés
    /// (`?tag=`, `utm_*`… changent à chaque partage sans changer le produit).
    static func sameURL(_ lhs: String, _ rhs: String) -> Bool {
        guard let a = canonical(lhs), let b = canonical(rhs) else { return false }
        return a == b
    }

    private static func canonical(_ raw: String) -> String? {
        guard let components = URLComponents(string: raw.trimmingCharacters(in: .whitespacesAndNewlines)),
              let host = components.host?.lowercased() else { return nil }
        let path = components.path.hasSuffix("/") ? String(components.path.dropLast()) : components.path
        return host.replacingOccurrences(of: "^www\\.", with: "", options: .regularExpression) + path.lowercased()
    }
}
