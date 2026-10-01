// Génère l'icône d'app provisoire (1024 px, sans transparence) à partir du symbole SF "gift.fill".
// Usage : swift scripts/apple/make-icon.swift <sortie.png> && magick <sortie.png> -background "#FEF6EC" -alpha remove -alpha off <sortie.png>
import AppKit

let size = 1024.0
let out = CommandLine.arguments.dropFirst().first ?? "AppIcon.png"
let rep = NSBitmapImageRep(bitmapDataPlanes: nil, pixelsWide: Int(size), pixelsHigh: Int(size), bitsPerSample: 8,
                           samplesPerPixel: 4, hasAlpha: true, isPlanar: false, colorSpaceName: .deviceRGB,
                           bytesPerRow: 0, bitsPerPixel: 0)!
NSGraphicsContext.saveGraphicsState()
NSGraphicsContext.current = NSGraphicsContext(bitmapImageRep: rep)

let bg = NSGradient(starting: NSColor(srgbRed: 0.996, green: 0.965, blue: 0.925, alpha: 1),
                    ending: NSColor(srgbRed: 0.976, green: 0.851, blue: 0.863, alpha: 1))!
bg.draw(in: NSRect(x: 0, y: 0, width: size, height: size), angle: -90)

let config = NSImage.SymbolConfiguration(pointSize: 560, weight: .semibold)
    .applying(.init(paletteColors: [NSColor(srgbRed: 0.894, green: 0.251, blue: 0.231, alpha: 1)]))
if let symbol = NSImage(systemSymbolName: "gift.fill", accessibilityDescription: nil)?.withSymbolConfiguration(config) {
    let s = symbol.size
    let scale = 620 / max(s.width, s.height)
    let w = s.width * scale, h = s.height * scale
    symbol.draw(in: NSRect(x: (size - w) / 2, y: (size - h) / 2 - 10, width: w, height: h))
}
NSGraphicsContext.restoreGraphicsState()
try! rep.representation(using: .png, properties: [:])!.write(to: URL(fileURLWithPath: out))
print("✓ \(out)")
