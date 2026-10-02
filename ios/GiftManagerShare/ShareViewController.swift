import SwiftUI
import UIKit
import UniformTypeIdentifiers

/// Extension « Partager → Gift Manager » (#34) : récupère le lien partagé et affiche le formulaire d'ajout.
final class ShareViewController: UIViewController {
    override func viewDidLoad() {
        super.viewDidLoad()
        FCSystemAppearance.apply()
        Task { @MainActor in
            let link = await sharedLink()
            let host = UIHostingController(rootView: ShareView(sharedText: link) { [weak self] in
                self?.extensionContext?.completeRequest(returningItems: nil)
            })
            addChild(host)
            host.view.frame = view.bounds
            host.view.autoresizingMask = [.flexibleWidth, .flexibleHeight]
            view.addSubview(host.view)
            host.didMove(toParent: self)
        }
    }

    /// Premier lien (ou texte contenant un lien) reçu de l'app qui partage.
    private func sharedLink() async -> String {
        let providers = (extensionContext?.inputItems as? [NSExtensionItem] ?? []).flatMap { $0.attachments ?? [] }
        for provider in providers where provider.hasItemConformingToTypeIdentifier(UTType.url.identifier) {
            if let url = try? await provider.loadItem(forTypeIdentifier: UTType.url.identifier) as? URL {
                return url.absoluteString
            }
        }
        for provider in providers where provider.hasItemConformingToTypeIdentifier(UTType.plainText.identifier) {
            if let text = try? await provider.loadItem(forTypeIdentifier: UTType.plainText.identifier) as? String {
                return text
            }
        }
        return ""
    }
}
