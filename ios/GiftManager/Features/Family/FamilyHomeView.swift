import SwiftUI

/// « Notre famille » (écran 2 de la maquette) : événements, membres, paramètres (#6, #8).
struct FamilyHomeView: View {
    @Environment(AppState.self) private var appState
    @State private var tab = 0
    @State private var editingEvent: EventEditorView.Mode?

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: Spacing.l) {
                    header
                    SegmentedTabs(selection: $tab, titles: ["Événements", "Membres", "Paramètres"])
                    switch tab {
                    case 0: eventsSection
                    case 1: MembersSection()
                    default: GroupSettingsSection()
                    }
                }
                .padding(.horizontal, Spacing.xl)
                .padding(.bottom, Spacing.xxl)
            }
            .refreshable { await appState.reloadGroup() }
            .fcScreenBackground()
            .navigationTitle(appState.currentGroup?.name ?? "Notre famille")
            .navigationDestination(for: GiftEvent.self) { EventDetailView(event: $0) }
            .navigationDestination(for: ChildDestination.self) { ChildGiftsView(child: $0.child, eventId: $0.eventId, readOnly: $0.readOnly) }
            .sheet(item: $editingEvent) { EventEditorView(mode: $0) }
        }
    }

    private var header: some View {
        VStack(alignment: .leading, spacing: Spacing.s) {
            AvatarStack(names: appState.members.map(\.displayName))
            Text("\(appState.members.count) membre\(appState.members.count > 1 ? "s" : "") · \(appState.households.count) foyer\(appState.households.count > 1 ? "s" : "")")
                .font(Font.Theme.caption)
                .foregroundStyle(Color.Theme.textSecondary)
        }
        .padding(.top, Spacing.s)
    }

    @ViewBuilder
    private var eventsSection: some View {
        SectionHeader(title: "Événements à venir", actionSystemImage: "plus") { editingEvent = .create }
        if appState.upcomingEvents.isEmpty {
            EmptyStateView(imageName: "mascot_sleeping", title: "Aucun événement",
                           message: "Ajoute un anniversaire ou une fête.", actionTitle: "Ajouter un événement") {
                editingEvent = .create
            }
        }
        ForEach(appState.upcomingEvents) { event in
            NavigationLink(value: event) { eventRow(event) }
                .buttonStyle(.plain)
                .contextMenu {
                    Button("Modifier", systemImage: "pencil") { editingEvent = .edit(event) }
                }
        }
        if !appState.pastEvents.isEmpty {
            SectionHeader(title: "Événements passés")
            ForEach(appState.pastEvents) { event in
                NavigationLink(value: event) { eventRow(event) }
                    .buttonStyle(.plain)
                    .opacity(0.7)
            }
        }
    }

    private func eventRow(_ event: GiftEvent) -> some View {
        let children = appState.children(for: event)
        var subtitle: String? = event.isPast ? nil : Formatting.countdownText(days: event.daysRemaining)
        if event.kind == .birthday, let child = appState.child(event.childId), let age = child.age(on: event.eventDate.localDate) {
            subtitle = [subtitle, Formatting.ageText(age)].compactMap { $0 }.joined(separator: " · ")
        }
        return EventRow(title: event.title, dateText: Formatting.dateText(event.eventDate), subtitle: subtitle,
                        kind: event.kind.designKind, avatarNames: children.map(\.firstName))
    }
}

struct ChildDestination: Hashable {
    let child: Child
    let eventId: UUID?
    /// Événement passé : liste en lecture seule.
    var readOnly = false
}

extension GiftEventKind {
    var designKind: EventKind {
        switch self {
        case .christmas: .christmas
        case .birthday: .birthday
        case .other: .other
        }
    }
}

// MARK: - Membres

private struct MembersSection: View {
    @Environment(AppState.self) private var appState
    @State private var editingChild: ChildEditorView.Mode?

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.l) {
            if let mine = appState.myHousehold {
                householdCard(mine, isMine: true)
            }
            ForEach(appState.households.filter { $0.id != appState.myHousehold?.id }) { household in
                householdCard(household, isMine: false)
            }
        }
        .sheet(item: $editingChild) { ChildEditorView(mode: $0) }
    }

    private func householdCard(_ household: Household, isMine: Bool) -> some View {
        VStack(alignment: .leading, spacing: Spacing.m) {
            HStack {
                CountryFlag(code: household.country)
                Text(household.name)
                    .font(Font.Theme.headline)
                    .foregroundStyle(Color.Theme.textPrimary)
                Spacer()
                if isMine {
                    Text("Mon foyer")
                        .font(Font.Theme.captionBold)
                        .foregroundStyle(Color.Theme.secondary)
                }
            }
            let parents = appState.parents(of: household)
            if !parents.isEmpty {
                Text(parents.map(\.displayName).joined(separator: " & "))
                    .font(Font.Theme.caption)
                    .foregroundStyle(Color.Theme.textSecondary)
            }
            ForEach(appState.children(of: household)) { child in
                HStack {
                    NavigationLink(value: ChildDestination(child: child, eventId: appState.currentEvent?.id)) {
                        ChildRow(child: child)
                        Spacer()
                        Image(systemName: "chevron.right")
                            .font(.footnote.weight(.semibold))
                            .foregroundStyle(Color.Theme.textSecondary)
                    }
                    .buttonStyle(.plain)
                    if isMine {
                        Button {
                            editingChild = .edit(child)
                        } label: {
                            Image(systemName: "pencil.circle")
                                .font(.title3)
                                .frame(width: 44, height: 44)
                        }
                        .accessibilityLabel("Modifier \(child.firstName)")
                    }
                }
            }
            if isMine {
                TextLinkButton(title: "+ Ajouter un enfant") { editingChild = .create }
                if let code = household.inviteCode {
                    ShareLink(item: "Rejoins notre foyer « \(household.name) » sur Famille Cadeaux avec le code : \(code)") {
                        Label("Code du foyer pour mon conjoint : \(code)", systemImage: "person.badge.plus")
                            .font(Font.Theme.caption)
                    }
                }
            }
        }
        .fcCard()
    }
}

// MARK: - Paramètres

private struct GroupSettingsSection: View {
    @Environment(AppState.self) private var appState
    @State private var groupName = ""
    @State private var showJoin = false
    @State private var showCreate = false

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.l) {
            if let group = appState.currentGroup {
                VStack(alignment: .leading, spacing: Spacing.m) {
                    Text("Inviter la famille").font(Font.Theme.headline)
                    Text("Envoie ce code aux autres foyers (WhatsApp, iMessage…).")
                        .font(Font.Theme.caption)
                        .foregroundStyle(Color.Theme.textSecondary)
                    Text(group.inviteCode)
                        .font(.system(.largeTitle, design: .monospaced).weight(.bold))
                        .foregroundStyle(Color.Theme.primary)
                        .frame(maxWidth: .infinity)
                        .textSelection(.enabled)
                        .accessibilityLabel("Code d'invitation \(group.inviteCode.map(String.init).joined(separator: " "))")
                    ShareLink(item: AppState.inviteMessage(for: group)) {
                        Label("Partager l'invitation", systemImage: "square.and.arrow.up")
                            .font(Font.Theme.headline)
                            .frame(maxWidth: .infinity, minHeight: 50)
                            .background(Color.Theme.primary, in: Capsule())
                            .foregroundStyle(Color.Theme.onPrimary)
                    }
                }
                .fcCard()

                VStack(alignment: .leading, spacing: Spacing.m) {
                    Text("Nom de la famille").font(Font.Theme.headline)
                    FCTextField(title: "Nom", text: $groupName, systemImage: "person.3")
                    SecondaryButton(title: "Renommer", systemImage: "pencil") {
                        Task {
                            do {
                                try await appState.repository.renameGroup(group.id, name: groupName.trimmed)
                                await appState.refreshAll()
                            } catch {
                                appState.report(error)
                            }
                        }
                    }
                    .disabled(groupName.trimmed.isEmpty || groupName.trimmed == group.name)
                }
                .fcCard()
                .onAppear { groupName = group.name }
            }

            if appState.groups.count > 1 {
                VStack(alignment: .leading, spacing: Spacing.m) {
                    Text("Mes familles").font(Font.Theme.headline)
                    ForEach(appState.groups) { group in
                        Button {
                            Task { await appState.selectGroup(group) }
                        } label: {
                            HStack {
                                Text(group.name).foregroundStyle(Color.Theme.textPrimary)
                                Spacer()
                                if group.id == appState.currentGroup?.id {
                                    Image(systemName: "checkmark").foregroundStyle(Color.Theme.secondary)
                                }
                            }
                            .frame(minHeight: 44)
                        }
                    }
                }
                .fcCard()
            }

            TextLinkButton(title: "Rejoindre une autre famille avec un code") { showJoin = true }
        }
        .alert("Rejoindre une famille", isPresented: $showJoin) {
            JoinOtherGroupAlert()
        }
    }
}

private struct JoinOtherGroupAlert: View {
    @Environment(AppState.self) private var appState
    @State private var code = ""

    var body: some View {
        TextField("Code", text: $code)
            .textInputAutocapitalization(.characters)
        Button("Rejoindre") {
            Task {
                if await appState.joinGroup(code: code.trimmed) != nil { await appState.refreshAll() }
            }
        }
        Button("Annuler", role: .cancel) {}
    }
}
