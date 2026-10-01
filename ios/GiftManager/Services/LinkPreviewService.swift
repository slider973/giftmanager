import Foundation
import LinkPresentation
import UIKit

/// Aperçu d'un lien produit : titre, image, prix si la page les expose.
struct LinkPreview: Equatable, Sendable {
    var title: String?
    var imageURL: URL?
    /// Image récupérée par LinkPresentation quand la page n'expose pas d'URL d'image exploitable.
    var imageData: Data?
    var price: Decimal?
    var currency: String?
}

/// Combine deux sources : les balises Open Graph / produit de la page (image en URL, prix),
/// puis LinkPresentation en secours (titre et image, y compris quand la page bloque les robots).
enum LinkPreviewService {
    static func preview(for urlString: String) async -> LinkPreview? {
        guard let url = normalizedURL(urlString) else { return nil }
        var preview = await fetchMeta(url) ?? LinkPreview()
        if preview.title == nil || preview.imageURL == nil {
            let fallback = await linkPresentation(url)
            preview.title = preview.title ?? fallback?.title
            if preview.imageURL == nil { preview.imageData = fallback?.imageData }
        }
        return preview.title == nil && preview.imageURL == nil && preview.imageData == nil ? nil : preview
    }

    static func normalizedURL(_ raw: String) -> URL? {
        var text = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        // Texte partagé du type « Regarde ça https://… » : on extrait le premier lien.
        if let detector = try? NSDataDetector(types: NSTextCheckingResult.CheckingType.link.rawValue),
           let match = detector.firstMatch(in: text, range: NSRange(text.startIndex..., in: text)),
           let range = Range(match.range, in: text) {
            text = String(text[range])
        }
        if !text.lowercased().hasPrefix("http") { text = "https://" + text }
        guard let url = URL(string: text), url.host() != nil else { return nil }
        return url
    }

    // MARK: - Balises meta

    private static func fetchMeta(_ url: URL) async -> LinkPreview? {
        var request = URLRequest(url: url, timeoutInterval: 8)
        request.setValue("Mozilla/5.0 (iPhone; CPU iPhone OS 18_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.0 Mobile/15E148 Safari/604.1",
                         forHTTPHeaderField: "User-Agent")
        request.setValue("fr-CH,fr;q=0.9,en;q=0.5", forHTTPHeaderField: "Accept-Language")
        guard let (data, response) = try? await URLSession.shared.data(for: request),
              (response as? HTTPURLResponse)?.statusCode ?? 0 < 400,
              let html = String(data: data.prefix(600_000), encoding: .utf8) ?? String(data: data.prefix(600_000), encoding: .isoLatin1)
        else { return nil }

        let meta = metaTags(in: html)
        var preview = LinkPreview()
        preview.title = (meta["og:title"] ?? meta["twitter:title"] ?? titleTag(in: html)).map(cleanTitle)
        if let image = meta["og:image"] ?? meta["og:image:secure_url"] ?? meta["twitter:image"] {
            preview.imageURL = URL(string: image, relativeTo: url)?.absoluteURL
        }
        let priceText = meta["product:price:amount"] ?? meta["og:price:amount"] ?? jsonLDPrice(in: html)
        preview.price = priceText.flatMap(parsePrice)
        preview.currency = meta["product:price:currency"] ?? meta["og:price:currency"] ?? jsonLDCurrency(in: html)
        return preview
    }

    private static func metaTags(in html: String) -> [String: String] {
        var result: [String: String] = [:]
        let pattern = #"<meta\s+[^>]*>"#
        guard let regex = try? NSRegularExpression(pattern: pattern, options: [.caseInsensitive]) else { return result }
        for match in regex.matches(in: html, range: NSRange(html.startIndex..., in: html)) {
            guard let range = Range(match.range, in: html) else { continue }
            let tag = String(html[range])
            guard let key = attribute("property", in: tag) ?? attribute("name", in: tag) ?? attribute("itemprop", in: tag),
                  let content = attribute("content", in: tag) else { continue }
            let lower = key.lowercased()
            if result[lower] == nil { result[lower] = decodeEntities(content) }
        }
        return result
    }

    private static func attribute(_ name: String, in tag: String) -> String? {
        let pattern = name + #"\s*=\s*["']([^"']*)["']"#
        guard let regex = try? NSRegularExpression(pattern: pattern, options: [.caseInsensitive]),
              let match = regex.firstMatch(in: tag, range: NSRange(tag.startIndex..., in: tag)),
              let range = Range(match.range(at: 1), in: tag) else { return nil }
        return String(tag[range])
    }

    private static func titleTag(in html: String) -> String? {
        guard let regex = try? NSRegularExpression(pattern: #"<title[^>]*>([^<]+)</title>"#, options: [.caseInsensitive]),
              let match = regex.firstMatch(in: html, range: NSRange(html.startIndex..., in: html)),
              let range = Range(match.range(at: 1), in: html) else { return nil }
        return decodeEntities(String(html[range]))
    }

    private static func jsonLDPrice(in html: String) -> String? {
        firstCapture(#""price"\s*:\s*"?([0-9]+(?:[.,][0-9]{1,2})?)"#, in: html)
    }

    private static func jsonLDCurrency(in html: String) -> String? {
        firstCapture(#""priceCurrency"\s*:\s*"([A-Z]{3})""#, in: html)
    }

    private static func firstCapture(_ pattern: String, in text: String) -> String? {
        guard let regex = try? NSRegularExpression(pattern: pattern),
              let match = regex.firstMatch(in: text, range: NSRange(text.startIndex..., in: text)),
              let range = Range(match.range(at: 1), in: text) else { return nil }
        return String(text[range])
    }

    /// Lit un prix saisi ou extrait d'une page : « 199,99 », « 1.299,00 », « 1'299.90 », « CHF 49.– ».
    /// Le dernier séparateur (point ou virgule) suivi d'au plus 2 chiffres est le séparateur décimal.
    static func parsePrice(_ text: String) -> Decimal? {
        var digits = text.filter { $0.isNumber || $0 == "." || $0 == "," }
        guard !digits.isEmpty else { return nil }
        if let last = digits.lastIndex(where: { $0 == "." || $0 == "," }),
           digits.distance(from: last, to: digits.endIndex) - 1 <= 2 {
            let integer = digits[..<last].filter(\.isNumber)
            let fraction = digits[digits.index(after: last)...]
            digits = integer + "." + fraction
        } else {
            digits = digits.filter(\.isNumber)
        }
        guard let value = Decimal(string: digits), value < 100_000_000 else { return nil }
        return value
    }

    private static func cleanTitle(_ title: String) -> String {
        // « Produit : Amazon.fr: Jeux et Jouets » → « Produit »
        let separators = [" : Amazon", " | Galaxus", " | Digitec", " - Fnac", " | Fnac", " | Manor", " – "]
        var result = title.trimmingCharacters(in: .whitespacesAndNewlines)
        for separator in separators {
            if let range = result.range(of: separator) { result = String(result[..<range.lowerBound]) }
        }
        if result.hasPrefix("Amazon.fr") || result.hasPrefix("Amazon.com") {
            result = result.components(separatedBy: ": ").dropFirst().joined(separator: ": ")
        }
        return String(result.prefix(200))
    }

    private static func decodeEntities(_ text: String) -> String {
        guard text.contains("&") else { return text }
        let map = ["&amp;": "&", "&quot;": "\"", "&#39;": "'", "&apos;": "'", "&lt;": "<", "&gt;": ">", "&nbsp;": " ", "&#x27;": "'"]
        return map.reduce(text) { $0.replacingOccurrences(of: $1.key, with: $1.value) }
    }

    // MARK: - LinkPresentation

    @MainActor
    private static func linkPresentation(_ url: URL) async -> (title: String?, imageData: Data?)? {
        let provider = LPMetadataProvider()
        provider.timeout = 8
        guard let metadata = try? await provider.startFetchingMetadata(for: url) else { return nil }
        var imageData: Data?
        if let imageProvider = metadata.imageProvider {
            imageData = await withCheckedContinuation { continuation in
                _ = imageProvider.loadObject(ofClass: UIImage.self) { object, _ in
                    let image = object as? UIImage
                    continuation.resume(returning: image?.jpegData(compressionQuality: 0.8))
                }
            }
        }
        return (metadata.title.map(cleanTitle), imageData)
    }
}
