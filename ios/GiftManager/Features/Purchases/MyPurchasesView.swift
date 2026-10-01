import SwiftUI

/// « Mes achats » : mes réservations par événement puis par enfant, total par devise (#12).
struct MyPurchasesView: View {
    @Environment(AppState.self) private var appState
    @Environment(\.openURL) private var openURL
    @State private var reservations: [MyReservation] = []
    @State private var links: [UUID: [ItemLink]] = [:]
    @State private var hasLoaded = false

    private struct EventGroup: Identifiable {
        let id: String
        let title: String
        let date: DayDate?
        let children: [(name: String, items: [MyReservation])]
    }

    private var groups: [EventGroup] {
        let byEvent = Dictionary(grouping: reservations) { $0.eventId?.uuidString ?? "none" }
        return byEvent.map { key, items in
            let byChild = Dictionary(grouping: items, by: \.childName)
                .sorted { $0.key < $1.key }
                .map { (name: $0.key, items: $0.value.sorted { $0.title < $1.title }) }
            return EventGroup(id: key, title: items.first?.eventTitle ?? "Sans événement",
                              date: items.first?.eventDate, children: byChild)
        }
        .sorted { ($0.date?.date ?? .distantFuture) < ($1.date?.date ?? .distantFuture) }
    }

    private var remainingCount: Int { reservations.filter { $0.status == .reserved }.count }

    /// Total par devise, à partir du lien le plus pertinent de chaque cadeau.
    private var totals: [(currency: String, amount: Decimal)] {
        var sums: [String: Decimal] = [:]
        for reservation in reservations {
            guard let link = bestLink(reservation.itemId), let price = link.price else { continue }
            sums[link.currency ?? "EUR", default: 0] += price
        }
        return sums.sorted { $0.key < $1.key }.map { (currency: $0.key, amount: $0.value) }
    }

    var body: some View {
        NavigationStack {
            List {
                if hasLoaded && reservations.isEmpty {
                    EmptyStateView(imageName: "mascot_sleeping", title: "Aucun achat prévu",
                                   message: "Réserve un cadeau dans la liste d'un enfant : il apparaîtra ici.")
                        .listRowBackground(Color.clear)
                        .listRowSeparator(.hidden)
                }
                if !reservations.isEmpty {
                    summary
                        .listRowBackground(Color.clear)
                        .listRowSeparator(.hidden)
                }
                ForEach(groups) { group in
                    Section {
                        ForEach(group.children, id: \.name) { child in
                            Text(child.name)
                                .font(Font.Theme.captionBold)
                                .foregroundStyle(Color.Theme.textSecondary)
                                .listRowBackground(Color.clear)
                                .listRowSeparator(.hidden)
                            ForEach(child.items) { reservation in
                                row(reservation)
                                    .listRowBackground(Color.clear)
                                    .listRowSeparator(.hidden)
                                    .swipeActions(edge: .trailing) {
                                        Button("Annuler", role: .destructive) {
                                            Task { await cancel(reservation) }
                                        }
                                    }
                            }
                        }
                    } header: {
                        Text(group.title)
                            .font(Font.Theme.headline)
                            .foregroundStyle(Color.Theme.textPrimary)
                            .textCase(nil)
                    }
                }
            }
            .listStyle(.plain)
            .scrollContentBackground(.hidden)
            .fcScreenBackground()
            .navigationTitle("Mes achats")
            .refreshable { await load() }
            .task(id: appState.itemsRevision) { await load() }
        }
    }

    private var summary: some View {
        HStack(spacing: Spacing.l) {
            VStack(alignment: .leading, spacing: 2) {
                Text("\(remainingCount)")
                    .font(Font.Theme.title)
                    .foregroundStyle(Color.Theme.textPrimary)
                Text(remainingCount > 1 ? "cadeaux à acheter" : "cadeau à acheter")
                    .font(Font.Theme.caption)
                    .foregroundStyle(Color.Theme.textSecondary)
            }
            Divider().frame(height: 36)
            VStack(alignment: .leading, spacing: 2) {
                Text(totals.isEmpty ? "—" : totals.compactMap { Money.format($0.amount, currency: $0.currency) }.joined(separator: " + "))
                    .font(Font.Theme.headline)
                    .foregroundStyle(Color.Theme.textPrimary)
                Text("Budget estimé")
                    .font(Font.Theme.caption)
                    .foregroundStyle(Color.Theme.textSecondary)
            }
            Spacer()
        }
        .fcCard()
        .accessibilityElement(children: .combine)
    }

    private func row(_ reservation: MyReservation) -> some View {
        let link = bestLink(reservation.itemId)
        return HStack(spacing: Spacing.m) {
            RemoteImage(url: reservation.imageUrl.flatMap(URL.init(string:)))
                .frame(width: 56, height: 56)
                .clipShape(RoundedRectangle(cornerRadius: Radius.thumb, style: .continuous))
            VStack(alignment: .leading, spacing: 4) {
                Text(reservation.title)
                    .font(Font.Theme.headline)
                    .foregroundStyle(Color.Theme.textPrimary)
                    .lineLimit(2)
                HStack(spacing: Spacing.xs) {
                    if let country = link?.country { CountryFlag(code: country) }
                    Text([link?.priceText, link?.store].compactMap { $0 }.joined(separator: " · "))
                        .font(Font.Theme.caption)
                        .foregroundStyle(Color.Theme.textSecondary)
                }
                if reservation.owned {
                    Label("Les parents l'ont noté comme déjà possédé", systemImage: "exclamationmark.triangle")
                        .font(Font.Theme.caption)
                        .foregroundStyle(Color.Theme.takenFg)
                }
            }
            Spacer(minLength: 0)
            VStack(spacing: Spacing.xs) {
                Button {
                    Task { await toggle(reservation) }
                } label: {
                    Image(systemName: reservation.status == .purchased ? "checkmark.circle.fill" : "circle")
                        .font(.title2)
                        .foregroundStyle(reservation.status == .purchased ? Color.Theme.availableFg : Color.Theme.textSecondary)
                        .frame(width: 44, height: 44)
                }
                .buttonStyle(.plain)
                .accessibilityLabel(reservation.status == .purchased ? "Acheté" : "Marquer comme acheté")
                if let url = link.flatMap({ URL(string: $0.url) }) {
                    Button {
                        openURL(url)
                    } label: {
                        Image(systemName: "arrow.up.right.square")
                            .frame(width: 44, height: 32)
                    }
                    .buttonStyle(.plain)
                    .foregroundStyle(Color.Theme.primary)
                    .accessibilityLabel("Ouvrir la boutique")
                }
            }
        }
        .fcCard(padding: Spacing.m)
    }

    private func bestLink(_ itemId: UUID) -> ItemLink? {
        StoreCatalog.sorted(links[itemId] ?? [], preferredCountry: appState.profile?.country).first
    }

    private func load() async {
        do {
            reservations = try await appState.repository.myReservations()
            let fetched = try await appState.repository.links(itemIds: reservations.map(\.itemId))
            links = Dictionary(grouping: fetched, by: \.itemId)
            await NotificationService.shared.scheduleReminders(reservations: reservations, events: appState.events)
        } catch {
            appState.report(error)
        }
        hasLoaded = true
    }

    private func toggle(_ reservation: MyReservation) async {
        do {
            try await appState.repository.setPurchased(itemId: reservation.itemId, purchased: reservation.status != .purchased)
            await load()
        } catch {
            appState.report(error)
        }
    }

    private func cancel(_ reservation: MyReservation) async {
        do {
            try await appState.repository.cancelReservation(itemId: reservation.itemId)
            appState.itemsChanged()
        } catch {
            appState.report(error)
        }
    }
}
