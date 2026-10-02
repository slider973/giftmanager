import Foundation
import LinkPresentation
import UIKit
import WebKit

/// Aperçu d'un lien produit : titre, image, prix et devise si la page les expose.
struct LinkPreview: Equatable, Sendable {
    var title: String?
    var imageURL: URL?
    /// Image récupérée par LinkPresentation quand la page n'expose pas d'URL d'image exploitable.
    var imageData: Data?
    var price: Decimal?
    var currency: String?
}

/// Lit la page produit comme Safari (WebKit) : beaucoup de boutiques (Galaxus, Digitec, Fnac…) refusent
/// les requêtes HTTP simples, et le prix n'est souvent disponible qu'après exécution du JavaScript.
/// LinkPresentation sert de secours pour le titre et l'image.
enum LinkPreviewService {
    static func preview(for urlString: String) async -> LinkPreview? {
        guard let url = normalizedURL(urlString) else { return nil }
        var preview = await WebPageScraper.scrape(url) ?? LinkPreview()
        if preview.title == nil || (preview.imageURL == nil && preview.imageData == nil) {
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

    // MARK: - Prix et devise

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

    /// Premier montant d'un texte affiché, ex. « 157,09 € avec 13 % d'économies » → 157.09.
    static func priceFromDisplayedText(_ text: String) -> Decimal? {
        let pattern = #"\d{1,3}(?:[ '’.,  ]\d{3})*(?:[.,]\d{1,2})?(?!\d)|\d+(?:[.,]\d{1,2})?"#
        guard let regex = try? NSRegularExpression(pattern: pattern),
              let match = regex.firstMatch(in: text, range: NSRange(text.startIndex..., in: text)),
              let range = Range(match.range, in: text) else { return nil }
        return parsePrice(String(text[range]))
    }

    /// Devise d'un prix affiché (« 149,31 CHF », « 159,99 € », « £20 », « CA$ 30 »).
    /// Le symbole `$` seul est résolu avec le pays de la boutique (USD par défaut).
    static func currency(inDisplayedText text: String, storeCountry: String?) -> String? {
        let upper = text.uppercased()
        for code in ["CHF", "EUR", "GBP", "USD", "CAD"] where upper.contains(code) { return code }
        if upper.contains("FR.") || upper.contains("SFR") { return "CHF" }
        if text.contains("€") { return "EUR" }
        if text.contains("£") { return "GBP" }
        if upper.contains("CA$") || upper.contains("C$") { return "CAD" }
        if text.contains("$") { return storeCountry == "CA" ? "CAD" : "USD" }
        return nil
    }

    static func cleanTitle(_ title: String) -> String {
        // « Produit : Amazon.fr: Jeux et Jouets » → « Produit »
        let separators = [" : Amazon", ": Amazon.", " | Galaxus", " - acheter sur Galaxus", " | Digitec", " - acheter sur Digitec",
                          " - Fnac", " | Fnac", " | Manor", " | Smyths Toys", " – "]
        var result = title.trimmingCharacters(in: .whitespacesAndNewlines)
        for separator in separators {
            if let range = result.range(of: separator) { result = String(result[..<range.lowerBound]) }
        }
        if result.hasPrefix("Amazon.fr") || result.hasPrefix("Amazon.com") {
            result = result.components(separatedBy: ": ").dropFirst().joined(separator: ": ")
        }
        return String(result.trimmingCharacters(in: .whitespaces).prefix(200))
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

/// Chargement invisible d'une page dans WebKit puis extraction des données produit.
@MainActor
final class WebPageScraper: NSObject, WKNavigationDelegate {
    private let webView: WKWebView
    private var continuation: CheckedContinuation<LinkPreview?, Never>?
    private let url: URL
    private let store: StoreCatalog.Store?
    private var attempts = 0

    /// Devise d'origine des sites Amazon : sans elle, Amazon convertit le prix dans la devise du visiteur
    /// (ex. CHF depuis la Suisse sur amazon.fr).
    private static let amazonCurrency = ["amazon.fr": "EUR", "amazon.de": "EUR", "amazon.it": "EUR", "amazon.es": "EUR",
                                         "amazon.com.be": "EUR", "amazon.nl": "EUR", "amazon.co.uk": "GBP",
                                         "amazon.com": "USD", "amazon.ca": "CAD"]

    static func scrape(_ url: URL, timeout: Duration = .seconds(15)) async -> LinkPreview? {
        let scraper = WebPageScraper(url: url)
        return await withTaskGroup(of: LinkPreview?.self) { group in
            group.addTask { await scraper.run() }
            group.addTask {
                try? await Task.sleep(for: timeout)
                await scraper.finish(nil)
                return nil
            }
            let first = await group.next() ?? nil
            group.cancelAll()
            return first
        }
    }

    private init(url: URL) {
        self.url = url
        self.store = StoreCatalog.store(for: url.absoluteString)
        let configuration = WKWebViewConfiguration()
        configuration.websiteDataStore = .nonPersistent()
        configuration.mediaTypesRequiringUserActionForPlayback = .all
        webView = WKWebView(frame: CGRect(x: 0, y: 0, width: 390, height: 844), configuration: configuration)
        webView.customUserAgent = "Mozilla/5.0 (iPhone; CPU iPhone OS 18_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.0 Mobile/15E148 Safari/604.1"
        super.init()
        webView.navigationDelegate = self
    }

    private func run() async -> LinkPreview? {
        await withCheckedContinuation { continuation in
            self.continuation = continuation
            Task { await self.load() }
        }
    }

    private func load() async {
        var request = URLRequest(url: url, timeoutInterval: 12)
        request.setValue("fr-CH,fr;q=0.9,de;q=0.7,en;q=0.5", forHTTPHeaderField: "Accept-Language")
        let host = (url.host() ?? "").replacingOccurrences(of: "www.", with: "")
        if let currency = Self.amazonCurrency[host],
           let cookie = HTTPCookie(properties: [.domain: "." + host, .path: "/", .name: "i18n-prefs", .value: currency,
                                                .secure: "TRUE", .expires: Date().addingTimeInterval(3600)]) {
            await webView.configuration.websiteDataStore.httpCookieStore.setCookie(cookie)
        }
        webView.load(request)
    }

    private func finish(_ preview: LinkPreview?) {
        guard let continuation else { return }
        self.continuation = nil
        webView.stopLoading()
        continuation.resume(returning: preview)
    }

    nonisolated func webView(_ webView: WKWebView, didFinish navigation: WKNavigation!) {
        Task { @MainActor in await self.extract(after: .milliseconds(1200)) }
    }

    nonisolated func webView(_ webView: WKWebView, didFail navigation: WKNavigation!, withError error: Error) {
        Task { @MainActor in self.finish(nil) }
    }

    nonisolated func webView(_ webView: WKWebView, didFailProvisionalNavigation navigation: WKNavigation!, withError error: Error) {
        Task { @MainActor in self.finish(nil) }
    }

    /// Extrait les données ; si le prix n'est pas encore rendu, réessaie deux fois.
    private func extract(after delay: Duration) async {
        try? await Task.sleep(for: delay)
        guard continuation != nil else { return }
        attempts += 1
        let raw = try? await webView.evaluateJavaScript(Self.extractionScript) as? String
        guard let data = raw?.data(using: .utf8),
              let page = try? JSONDecoder().decode(ScrapedPage.self, from: data) else {
            finish(nil)
            return
        }
        let preview = page.preview(baseURL: url, store: store)
        if preview.price == nil && attempts < 3 {
            await extract(after: .milliseconds(1500))
            return
        }
        finish(preview)
    }

    private struct ScrapedPage: Decodable {
        let title: String?
        let ogTitle: String?
        let ldName: String?
        let image: String?
        let price: String?
        let currency: String?
        let priceText: String?

        func preview(baseURL: URL, store: StoreCatalog.Store?) -> LinkPreview {
            var preview = LinkPreview()
            preview.title = (ogTitle ?? ldName ?? title).map(LinkPreviewService.cleanTitle).flatMap { $0.isEmpty ? nil : $0 }
            preview.imageURL = image.flatMap { URL(string: $0, relativeTo: baseURL)?.absoluteURL }
            if let price, let value = LinkPreviewService.parsePrice(price) {
                // Donnée structurée : devise déclarée par la page, sinon celle de la boutique.
                preview.price = value
                preview.currency = currency?.uppercased() ?? store?.currency
            } else if let priceText, let value = LinkPreviewService.priceFromDisplayedText(priceText) {
                // Prix affiché : la devise est celle du texte (Amazon peut convertir), sinon celle de la boutique.
                preview.price = value
                preview.currency = LinkPreviewService.currency(inDisplayedText: priceText, storeCountry: store?.country) ?? store?.currency
            }
            return preview
        }
    }

    /// Données produit, de la plus fiable à la moins fiable : JSON-LD Product, meta produit, itemprop, prix affiché.
    private static let extractionScript = #"""
    (() => {
      const out = { title: document.title || null };
      const metas = {};
      document.querySelectorAll('meta').forEach(m => {
        const k = (m.getAttribute('property') || m.getAttribute('name') || m.getAttribute('itemprop') || '').toLowerCase();
        const v = m.getAttribute('content');
        if (k && v && !(k in metas)) metas[k] = v;
      });
      out.ogTitle = metas['og:title'] || null;
      out.image = metas['og:image'] || metas['og:image:secure_url'] || metas['twitter:image'] || null;
      const isProduct = t => t === 'Product' || (Array.isArray(t) && t.includes('Product'));
      let ld = null;
      const walk = o => {
        if (!o || typeof o !== 'object' || ld) return;
        if (Array.isArray(o)) { o.forEach(walk); return; }
        if (isProduct(o['@type'])) {
          let off = o.offers; if (Array.isArray(off)) off = off[0];
          const spec = off && (Array.isArray(off.priceSpecification) ? off.priceSpecification[0] : off.priceSpecification);
          const im = o.image;
          ld = { name: o.name || null,
                 price: off ? (off.price ?? off.lowPrice ?? (spec && spec.price) ?? null) : null,
                 currency: off ? (off.priceCurrency ?? (spec && spec.priceCurrency) ?? null) : null,
                 image: Array.isArray(im) ? (im[0] && (im[0].url || im[0])) : ((im && im.url) || im || null) };
          return;
        }
        Object.values(o).forEach(walk);
      };
      document.querySelectorAll('script[type="application/ld+json"]').forEach(s => { try { walk(JSON.parse(s.textContent)); } catch (e) {} });
      out.ldName = ld && ld.name ? String(ld.name) : null;
      if (ld && ld.price != null) { out.price = String(ld.price); out.currency = ld.currency ? String(ld.currency) : null; }
      else if (metas['product:price:amount'] || metas['og:price:amount']) {
        out.price = metas['product:price:amount'] || metas['og:price:amount'];
        out.currency = metas['product:price:currency'] || metas['og:price:currency'] || null;
      } else {
        const ip = document.querySelector('[itemprop="price"]');
        if (ip) {
          out.price = ip.getAttribute('content') || ip.textContent.trim();
          const ic = document.querySelector('[itemprop="priceCurrency"]');
          out.currency = ic ? (ic.getAttribute('content') || ic.textContent.trim()) : null;
        }
      }
      if (!out.price) {
        const selectors = ['#corePrice_mobile_feature_div .a-offscreen', '#corePrice_feature_div .a-offscreen',
          '#corePriceDisplay_desktop_feature_div .a-offscreen', '.priceToPay .a-offscreen', '.apex-pricetopay-value .a-offscreen',
          '#apex-pricetopay-accessibility-label', '.reinventPricePriceToPayMargin', '#apex_desktop .a-offscreen',
          '#price_inside_buybox', '#sns-base-price', '.a-price .a-offscreen'];
        for (const sel of selectors) {
          const el = document.querySelector(sel);
          const text = el && el.textContent.trim();
          if (text && /\d/.test(text)) { out.priceText = text.slice(0, 80); break; }
        }
      }
      if (!out.image) {
        const img = document.querySelector('#landingImage, #imgBlkFront, #main-image');
        if (img) out.image = img.getAttribute('data-old-hires') || img.currentSrc || img.src || null;
      }
      if (!out.image && ld && ld.image) out.image = String(ld.image);
      return JSON.stringify(out);
    })()
    """#
}
