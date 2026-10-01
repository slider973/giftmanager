import SwiftUI

/// Drapeau d'un pays à partir de son code ISO 3166-1 alpha-2 (« CH », « fr »…).
/// Code absent ou invalide : globe. Hérite de la police de l'environnement.
struct CountryFlag: View {
    let code: String

    var body: some View {
        Group {
            if let flag = Self.flag(for: code) {
                Text(flag)
            } else {
                Image(systemName: "globe")
                    .foregroundStyle(Color.Theme.textSecondary)
            }
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(Self.countryName(for: code))
    }

    /// Code normalisé (majuscules, « UK » → « GB »), ou `nil` s'il n'est pas valide.
    static func normalized(_ code: String) -> String? {
        let upper = code.trimmingCharacters(in: .whitespacesAndNewlines).uppercased()
        let mapped = upper == "UK" ? "GB" : upper
        guard mapped.count == 2, mapped.unicodeScalars.allSatisfy({ ("A"..."Z").contains($0) }) else {
            return nil
        }
        return mapped
    }

    /// Émoji drapeau composé de deux indicateurs régionaux.
    static func flag(for code: String) -> String? {
        guard let iso = normalized(code) else { return nil }
        let base: UInt32 = 0x1F1E6 - 65 // « A » → 🇦
        var result = ""
        for scalar in iso.unicodeScalars {
            guard let indicator = Unicode.Scalar(base + scalar.value) else { return nil }
            result.unicodeScalars.append(indicator)
        }
        return result
    }

    /// Nom du pays en français (« Suisse »), pour VoiceOver.
    static func countryName(for code: String) -> String {
        guard let iso = normalized(code),
              let name = Locale(identifier: "fr_CH").localizedString(forRegionCode: iso) else {
            return "Pays inconnu"
        }
        return name
    }
}

#Preview("Clair") {
    HStack(spacing: Spacing.m) {
        ForEach(["CH", "FR", "DE", "IT", "BE", "uk", "", "X1"], id: \.self) { CountryFlag(code: $0) }
    }
    .font(.title2)
    .padding(Spacing.xl)
    .fcScreenBackground()
}

#Preview("Sombre") {
    HStack(spacing: Spacing.m) {
        ForEach(["CH", "FR", "DE", "IT", "BE", "uk", "", "X1"], id: \.self) { CountryFlag(code: $0) }
    }
    .font(.title2)
    .padding(Spacing.xl)
    .fcScreenBackground()
    .preferredColorScheme(.dark)
}
