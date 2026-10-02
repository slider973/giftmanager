import Supabase
import SwiftUI
import WidgetKit

/// Widget (#44) : prochain événement, jours restants, cadeaux qu'il me reste à acheter.
/// Ne montre que MES réservations (lues avec ma session partagée), jamais celles des autres.
@main
struct GiftManagerWidgets: WidgetBundle {
    var body: some Widget {
        CountdownWidget()
    }
}

struct CountdownEntry: TimelineEntry {
    enum State { case signedOut, noEvent, ready }
    let date: Date
    var state: State
    var eventTitle = ""
    var eventKind: GiftEventKind = .christmas
    var eventDate: Date = .now
    var toBuy = 0
    var purchased = 0

    var daysRemaining: Int {
        Calendar.current.dateComponents([.day], from: Calendar.current.startOfDay(for: date),
                                        to: Calendar.current.startOfDay(for: eventDate)).day ?? 0
    }

    static let placeholder = CountdownEntry(date: .now, state: .ready, eventTitle: "Noël 2026", eventKind: .christmas,
                                            eventDate: Calendar.current.date(byAdding: .day, value: 24, to: .now) ?? .now,
                                            toBuy: 2, purchased: 3)
}

struct CountdownProvider: TimelineProvider {
    func placeholder(in context: Context) -> CountdownEntry { .placeholder }

    func getSnapshot(in context: Context, completion: @escaping (CountdownEntry) -> Void) {
        if context.isPreview { completion(.placeholder); return }
        Task { completion(await Self.load()) }
    }

    func getTimeline(in context: Context, completion: @escaping (Timeline<CountdownEntry>) -> Void) {
        Task {
            let entry = await Self.load()
            // Rafraîchi toutes les 3 h et à minuit (changement de jour du compte à rebours).
            let midnight = Calendar.current.startOfDay(for: .now.addingTimeInterval(86_400))
            let next = min(Date.now.addingTimeInterval(3 * 3600), midnight)
            completion(Timeline(entries: [entry], policy: .after(next)))
        }
    }

    static func load() async -> CountdownEntry {
        let repository = GiftRepository()
        guard (try? await repository.client.auth.session) != nil else {
            return CountdownEntry(date: .now, state: .signedOut)
        }
        let groups = (try? await repository.myGroups()) ?? []
        var events: [GiftEvent] = []
        for group in groups {
            events += ((try? await repository.events(groupId: group.id)) ?? []).filter { !$0.isPast }
        }
        guard let next = events.min(by: { $0.eventDate < $1.eventDate }) else {
            return CountdownEntry(date: .now, state: .noEvent)
        }
        let reservations = ((try? await repository.myReservations()) ?? []).filter { $0.eventId == next.id || $0.eventId == nil }
        return CountdownEntry(date: .now, state: .ready, eventTitle: next.title, eventKind: next.kind,
                              eventDate: next.eventDate.localDate,
                              toBuy: reservations.filter { $0.status == .reserved && !$0.owned }.count,
                              purchased: reservations.filter { $0.status == .purchased }.count)
    }
}

struct CountdownWidget: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: "GiftManagerCountdown", provider: CountdownProvider()) { entry in
            CountdownWidgetView(entry: entry)
                .containerBackground(Color.Theme.background, for: .widget)
        }
        .configurationDisplayName("Compte à rebours")
        .description("Le prochain événement et les cadeaux qu'il te reste à acheter.")
        .supportedFamilies([.systemSmall, .systemMedium])
    }
}

struct CountdownWidgetView: View {
    @Environment(\.widgetFamily) private var family
    let entry: CountdownEntry

    var body: some View {
        switch entry.state {
        case .signedOut:
            message("Ouvre Gift Manager pour te connecter.", image: "person.crop.circle.badge.questionmark")
        case .noEvent:
            message("Aucun événement à venir.", image: "calendar")
        case .ready:
            if family == .systemMedium { medium } else { small }
        }
    }

    private var emoji: String {
        switch entry.eventKind {
        case .christmas: "🎄"
        case .birthday: "🎂"
        case .other: "🎁"
        }
    }

    private var daysText: String {
        switch entry.daysRemaining {
        case ..<1: "Aujourd'hui !"
        case 1: "Demain"
        default: "J-\(entry.daysRemaining)"
        }
    }

    private var small: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(emoji).font(.title2)
            Text(daysText)
                .font(.system(.title, design: .rounded).weight(.bold))
                .foregroundStyle(Color.Theme.primary)
                .minimumScaleFactor(0.7)
            Text(entry.eventTitle)
                .font(.caption.weight(.semibold))
                .foregroundStyle(Color.Theme.textPrimary)
                .lineLimit(1)
            Spacer(minLength: 0)
            Label(entry.toBuy == 0 ? "Tout est prêt" : "\(entry.toBuy) à acheter",
                  systemImage: entry.toBuy == 0 ? "checkmark.circle.fill" : "bag")
                .font(.caption2.weight(.semibold))
                .foregroundStyle(entry.toBuy == 0 ? Color.Theme.availableFg : Color.Theme.textSecondary)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .leading)
        .accessibilityElement(children: .combine)
    }

    private var medium: some View {
        HStack(spacing: 16) {
            small
            VStack(alignment: .leading, spacing: 8) {
                stat(value: entry.toBuy, label: "à acheter", color: Color.Theme.takenFg)
                stat(value: entry.purchased, label: "déjà achetés", color: Color.Theme.availableFg)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
    }

    private func stat(value: Int, label: String, color: Color) -> some View {
        HStack(alignment: .firstTextBaseline, spacing: 6) {
            Text("\(value)")
                .font(.system(.title2, design: .rounded).weight(.bold))
                .foregroundStyle(color)
            Text(label)
                .font(.caption)
                .foregroundStyle(Color.Theme.textSecondary)
        }
    }

    private func message(_ text: String, image: String) -> some View {
        VStack(spacing: 8) {
            Image(systemName: image).font(.title2).foregroundStyle(Color.Theme.primary)
            Text(text)
                .font(.caption)
                .multilineTextAlignment(.center)
                .foregroundStyle(Color.Theme.textSecondary)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }
}
