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
                    pendingIdeasBanner
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
            .navigationDestination(for: FamilyDestination.self) { destination in
                switch destination {
                case .pendingIdeas: PendingIdeasView()
                }
            }
            .sheet(item: $editingEvent) { EventEditorView(mode: $0) }
        }
    }

    private var header: some View {
        VStack(alignment: .leading, spacing: Spacing.s) {
            AvatarStack(names: appState.members.map(\.displayName), size: 40)
            Text("\(appState.members.count) membre\(appState.members.count > 1 ? "s" : "") · \(appState.households.count) foyer\(appState.households.count > 1 ? "s" : "")")
                .font(Font.Theme.caption)
                .foregroundStyle(Color.Theme.textSecondary)
        }
        .padding(.top, Spacing.s)
    }

    /// #57 — Idées proposées pour mes enfants, en attente de mon verdict.
    @ViewBuilder
    private var pendingIdeasBanner: some View {
        if appState.pendingIdeasCount > 0 {
            NavigationLink(value: FamilyDestination.pendingIdeas) {
                HStack(spacing: Spacing.m) {
                    Image(systemName: "lightbulb.fill")
                        .foregroundStyle(Color.Theme.accentAmber)
                        .accessibilityHidden(true)
                    VStack(alignment: .leading, spacing: Spacing.xs) {
                        Text(appState.pendingIdeasCount == 1
                             ? "Une idée attend ton avis"
                             : "\(appState.pendingIdeasCount) idées attendent ton avis")
                            .font(Font.Theme.headline)
                            .foregroundStyle(Color.Theme.textPrimary)
                        Text("Tu décides si le cadeau convient, sans savoir qui l'offre.")
                            .font(Font.Theme.caption)
                            .foregroundStyle(Color.Theme.textSecondary)
                    }
                    Spacer(minLength: Spacing.s)
                    Image(systemName: "chevron.right")
                        .foregroundStyle(Color.Theme.textSecondary)
                        .accessibilityHidden(true)
                }
                .frame(minHeight: HitTarget.minimum)
                .fcCard()
            }
            .buttonStyle(FCPressableStyle())
        }
    }

    @ViewBuilder
    private var eventsSection: some View {
        SectionHeader(title: "Événements à venir", actionSystemImage: "plus", action: { editingEvent = .create },
                      actionLabel: "Ajouter un événement")
        if appState.upcomingEvents.isEmpty {
            EmptyStateView(imageName: "mascot_sleeping", title: "Aucun événement",
                           message: "Ajoute un anniversaire ou une fête.", actionTitle: "Ajouter un événement") {
                editingEvent = .create
            }
        }
        ForEach(appState.upcomingEvents) { event in
            NavigationLink(value: event) { eventRow(event) }
                .buttonStyle(FCPressableStyle())
                .contextMenu {
                    Button("Modifier", systemImage: "pencil") { editingEvent = .edit(event) }
                }
        }
        if !appState.pastEvents.isEmpty {
            SectionHeader(title: "Événements passés")
                .padding(.top, Spacing.s)
            ForEach(appState.pastEvents) { event in
                // Archive : vignette désaturée, texte intact (une opacité casserait le contraste AA).
                NavigationLink(value: event) { eventRow(event).grayscale(0.7) }
                    .buttonStyle(FCPressableStyle())
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

/// Écrans de la famille atteints par un lien, sans objet à transporter.
enum FamilyDestination: Hashable {
    /// Idées proposées pour mes enfants, en attente de mon verdict (#57).
    case pendingIdeas
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
            HStack(spacing: Spacing.s) {
                CountryFlag(code: household.country)
                Text(household.name)
                    .font(Font.Theme.headline)
                    .foregroundStyle(Color.Theme.textPrimary)
                    .accessibilityAddTraits(.isHeader)
                Spacer(minLength: Spacing.s)
                if isMine {
                    Text("Mon foyer")
                        .font(Font.Theme.captionBold)
                        .foregroundStyle(Color.Theme.availableFg)
                        .padding(.horizontal, Spacing.s)
                        .padding(.vertical, Spacing.xs)
                        .background(Color.Theme.availableBg, in: Capsule())
                }
            }
            let parents = appState.parents(of: household)
            if !parents.isEmpty {
                Text(parents.map(\.displayName).joined(separator: " & "))
                    .font(Font.Theme.caption)
                    .foregroundStyle(Color.Theme.textSecondary)
            }
            ForEach(appState.children(of: household)) { child in
                HStack(spacing: Spacing.s) {
                    NavigationLink(value: ChildDestination(child: child, eventId: appState.currentEvent?.id)) {
                        HStack {
                            ChildRow(child: child)
                            Spacer(minLength: Spacing.s)
                            Image(systemName: "chevron.right")
                                .font(Font.Theme.callout.weight(.semibold))
                                .foregroundStyle(Color.Theme.textSecondary)
                                .accessibilityHidden(true)
                        }
                        .frame(minHeight: HitTarget.minimum)
                        .contentShape(Rectangle())
                    }
                    .buttonStyle(FCPressableStyle(pressedScale: 0.98))
                    if isMine {
                        Button {
                            editingChild = .edit(child)
                        } label: {
                            Image(systemName: "pencil")
                                .font(Font.Theme.callout.weight(.semibold))
                                .foregroundStyle(Color.Theme.primary)
                                .frame(width: 34, height: 34)
                                .background(Color.Theme.background, in: Circle())
                                .frame(width: HitTarget.minimum, height: HitTarget.minimum)
                                .contentShape(Rectangle())
                        }
                        .buttonStyle(FCPressableStyle(pressedScale: 0.9))
                        .padding(.trailing, -Spacing.s)
                        .accessibilityLabel("Modifier \(child.firstName)")
                    }
                }
            }
            if isMine {
                Divider().overlay(Color.Theme.separator)
                Button { editingChild = .create } label: {
                    Label("Ajouter un enfant", systemImage: "plus.circle.fill")
                        .font(Font.Theme.callout.weight(.medium))
                        .foregroundStyle(Color.Theme.primary)
                        .frame(minHeight: HitTarget.minimum)
                        .contentShape(Rectangle())
                }
                .buttonStyle(FCPressableStyle(pressedScale: 1))
                Button { editingChild = .createAdult } label: {
                    Label("Créer une liste d'adulte", systemImage: "person.crop.circle.badge.plus")
                        .font(Font.Theme.callout.weight(.medium))
                        .foregroundStyle(Color.Theme.primary)
                        .frame(minHeight: HitTarget.minimum)
                        .contentShape(Rectangle())
                }
                .buttonStyle(FCPressableStyle(pressedScale: 1))
                .accessibilityHint("Ta liste ou celle de ton conjoint, avec le même mode surprise que pour les enfants")
                if let code = household.inviteCode {
                    ShareLink(item: "Rejoins notre foyer « \(household.name) » sur Gift Manager avec le code : \(code)") {
                        HStack(alignment: .firstTextBaseline, spacing: Spacing.s) {
                            Image(systemName: "person.badge.plus")
                                .accessibilityHidden(true)
                            (Text("Code du foyer pour mon conjoint : ") + Text(code).monospaced().bold())
                                .frame(maxWidth: .infinity, alignment: .leading)
                            Image(systemName: "square.and.arrow.up")
                                .accessibilityHidden(true)
                        }
                        .font(Font.Theme.caption)
                        .foregroundStyle(Color.Theme.textSecondary)
                        .frame(minHeight: HitTarget.minimum)
                        .contentShape(Rectangle())
                    }
                    .buttonStyle(FCPressableStyle(pressedScale: 1))
                    .accessibilityHint("Partage le code du foyer")
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
                    Text("Inviter la famille")
                        .font(Font.Theme.headline)
                        .foregroundStyle(Color.Theme.textPrimary)
                        .accessibilityAddTraits(.isHeader)
                    Text("Envoie ce code aux autres foyers (WhatsApp, iMessage…).")
                        .font(Font.Theme.caption)
                        .foregroundStyle(Color.Theme.textSecondary)
                    Text(group.inviteCode)
                        .font(.system(.largeTitle, design: .monospaced).weight(.bold))
                        .tracking(4)
                        .foregroundStyle(Color.Theme.primary)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, Spacing.m)
                        .background(Color.Theme.background, in: RoundedRectangle(cornerRadius: Radius.field, style: .continuous))
                        .textSelection(.enabled)
                        .accessibilityLabel("Code d'invitation \(group.inviteCode.map(String.init).joined(separator: " "))")
                    ShareLink(item: AppState.inviteMessage(for: group)) {
                        FCPillLabel(title: "Partager l'invitation", systemImage: "square.and.arrow.up")
                    }
                    .buttonStyle(FCPressableStyle())
                }
                .fcCard()

                VStack(alignment: .leading, spacing: Spacing.m) {
                    FCTextField(title: "Nom de la famille", text: $groupName, systemImage: "person.3")
                    // Le bouton n'apparaît qu'une fois le nom modifié : pas de pilule grisée en permanence.
                    if !groupName.trimmed.isEmpty && groupName.trimmed != group.name {
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
                        .transition(.opacity)
                    }
                }
                .animation(.easeOut(duration: 0.2), value: groupName)
                .fcCard()
                .onAppear { groupName = group.name }
                // Sans ceci, changer de famille laisse le nom de la précédente dans le champ :
                // le bouton « Renommer » surgit seul et un tap écrase le nom de la nouvelle.
                .onChange(of: group.id) { groupName = group.name }
            }

            if appState.groups.count > 1 {
                VStack(alignment: .leading, spacing: Spacing.m) {
                    Text("Mes familles")
                        .font(Font.Theme.headline)
                        .foregroundStyle(Color.Theme.textPrimary)
                        .accessibilityAddTraits(.isHeader)
                    ForEach(appState.groups) { group in
                        Button {
                            Task { await appState.selectGroup(group) }
                        } label: {
                            HStack {
                                Text(group.name).foregroundStyle(Color.Theme.textPrimary)
                                Spacer()
                                if group.id == appState.currentGroup?.id {
                                    Image(systemName: "checkmark")
                                        .font(Font.Theme.callout.weight(.semibold))
                                        .foregroundStyle(Color.Theme.secondary)
                                }
                            }
                            .font(Font.Theme.callout)
                            .frame(minHeight: HitTarget.minimum)
                            .contentShape(Rectangle())
                        }
                        .buttonStyle(FCPressableStyle(pressedScale: 1))
                        .accessibilityAddTraits(group.id == appState.currentGroup?.id ? .isSelected : [])
                    }
                }
                .fcCard()
            }

            TextLinkButton(title: "Rejoindre une autre famille avec un code") { showJoin = true }
                .frame(maxWidth: .infinity)
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
