import AVFoundation
import Foundation
import Observation

/// Joue les répliques pré-générées du Père Noël depuis le bundle.
///
/// L'audio est livré avec l'application, jamais synthétisé à l'exécution : la
/// clé ElevenLabs ne quitte donc jamais le poste de développement, il n'y a
/// aucune latence réseau pendant qu'un enfant attend, et le contenu prononcé
/// est exactement celui qui a été relu.
@Observable
@MainActor
final class SantaVoicePlayer {
    private(set) var isPlaying = false
    /// Vrai si un fichier audio manquait : la vue affiche alors le texte seul.
    private(set) var audioUnavailable = false

    private var player: AVAudioPlayer?
    private var delegate: PlaybackDelegate?
    private let bundle: Bundle

    init(bundle: Bundle = .main) {
        self.bundle = bundle
    }

    /// Joue une réplique. `onFinish` est appelé à la fin, ou immédiatement si
    /// l'audio est introuvable — la session ne doit jamais rester bloquée.
    func play(resource: String, onFinish: @escaping () -> Void) {
        stop()

        guard let url = bundle.url(forResource: resource,
                                   withExtension: "mp3",
                                   subdirectory: "audio")
                ?? bundle.url(forResource: resource, withExtension: "mp3") else {
            audioUnavailable = true
            onFinish()
            return
        }

        do {
            try AVAudioSession.sharedInstance().setCategory(.playback, mode: .spokenAudio)
            try AVAudioSession.sharedInstance().setActive(true)

            let p = try AVAudioPlayer(contentsOf: url)
            let d = PlaybackDelegate { [weak self] in
                self?.isPlaying = false
                onFinish()
            }
            p.delegate = d
            delegate = d
            player = p
            audioUnavailable = false
            isPlaying = true
            p.play()
        } catch {
            // Un défaut de lecture ne doit pas interrompre la session :
            // la vue montre le texte et le parent le lit lui-même.
            audioUnavailable = true
            isPlaying = false
            onFinish()
        }
    }

    func stop() {
        player?.stop()
        player = nil
        delegate = nil
        isPlaying = false
    }

    /// `AVAudioPlayerDelegate` exige une classe NSObject ; ce petit adaptateur
    /// évite de faire de `SantaVoicePlayer` un NSObject.
    private final class PlaybackDelegate: NSObject, AVAudioPlayerDelegate {
        private let onFinish: () -> Void

        init(onFinish: @escaping () -> Void) {
            self.onFinish = onFinish
        }

        func audioPlayerDidFinishPlaying(_ player: AVAudioPlayer, successfully flag: Bool) {
            Task { @MainActor in self.onFinish() }
        }

        func audioPlayerDecodeErrorDidOccur(_ player: AVAudioPlayer, error: Error?) {
            Task { @MainActor in self.onFinish() }
        }
    }
}
