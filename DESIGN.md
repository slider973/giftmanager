# Gift Manager — Design

Référence visuelle : [`docs/design/maquette-v1.png`](docs/design/maquette-v1.png)

Nom affiché de l'app : **Gift Manager**. Promesse : *« Des idées. Moins de doublons. Plus de magie. »*

## Direction

Chaleureux, doux et festif sans être enfantin. Fond crème, cartes blanches très arrondies, un bleu nuit profond pour les actions principales, des pastels pour les catégories, des illustrations de mascottes (loutres) pour l'onboarding et les états vides. Lisible pour les grands-parents.

## Tokens

Source de vérité : color sets de [`Assets.xcassets/Theme/`](ios/GiftManager/Resources/Assets.xcassets/Theme) (namespace `Theme/`) et code de [`ios/GiftManager/DesignSystem/`](ios/GiftManager/DesignSystem). Les valeurs ci-dessous en sont le reflet ; ne jamais écrire d'hexadécimal dans un écran.

### Couleurs (`Color.Theme`)

Contrastes mesurés (WCAG 2.x) : toutes les paires texte / fond ≥ 4,5:1 dans les deux apparences.

| Token | Clair | Sombre | Usage | Contraste clé |
|---|---|---|---|---|
| `background` | `#FBF4EC` | `#141A2C` | Fond d'écran (crème / bleu nuit) | — |
| `surface` | `#FFFFFF` | `#232B43` | Cartes, champs, feuilles | — |
| `separator` | `#EAE2D6` | `#343D59` | Filets, bordures de champs, rail des onglets | décoratif |
| `primary` | `#1E2A47` | `#A9BCEE` | CTA principal, FAB +, onglet actif, liens texte | 13,0 / 9,2 sur `background` |
| `onPrimary` | `#FFFFFF` | `#141A2C` | Texte sur `primary` et `secondary` | 14,2 / 9,2 |
| `secondary` | `#2A7464` | `#6CC7AE` | CTA vert (fiche cadeau) | `onPrimary` 5,6 / 8,6 |
| `textPrimary` | `#1B1F2A` | `#F4F1EC` | Titres, noms | 15,1 / 15,4 |
| `textSecondary` | `#626673` | `#A8AEBF` | Métadonnées (prix, boutique, dates) | 5,3 / 7,8 sur `background` |
| `heart` | `#E5484D` | `#FF6B70` | Cœur « très envie » (icône) | 3,9 / 5,1 sur `surface` |
| `accentAmber` | `#B85F0E` | `#F2A65A` | Accent chaud (pastille « Surprise », placeholder d'image) | 4,5 / 6,9 |
| `availableBg` / `availableFg` | `#DDF3E6` / `#1E6B43` | `#1D3D2E` / `#8EDBB0` | Badge « Disponible » | 5,6 / 7,3 |
| `takenBg` / `takenFg` | `#FDE3E3` / `#B3302F` | `#45222A` / `#FFA3A3` | Badge « Déjà pris » | 5,1 / 7,3 |
| `mineBg` / `mineFg` | `#DCE8FB` / `#234E9A` | `#22365A` / `#A9C6FF` | Badge « Je l'offre » | 6,5 / 7,0 |
| `ownedBg` / `ownedFg` | `#ECE7FA` / `#5A47A6` | `#322B55` / `#CBBEFF` | Badge « Possède déjà » | 6,0 / 7,7 |
| `potBg` / `potFg` | `#FCEBD0` / `#8B4A0C` | `#47331A` / `#F7C98B` | Badge « Cagnotte », jauge de cagnotte, « Ma part » | 5,8 / 7,8 |
| `pastelPink` | `#F9D9DC` | `#4A2C36` | Groupes, anniversaires, avatars | — |
| `pastelMint` | `#D5F0E4` | `#1F4237` | Listes, Noël, avatars | — |
| `pastelBlue` | `#DCE8FB` | `#233A5E` | Liens / boutiques, avatars | — |
| `pastelPeach` | `#FCE3D2` | `#4A3326` | Surprise, placeholder d'image, avatars | — |
| `pastelLavender` | `#E6E1FA` | `#352F5C` | Protection, avatars | — |

Écarts assumés par rapport aux relevés de la maquette, pour passer AA : `secondary` `#2F7D6D` → `#2A7464`, `textSecondary` `#6B6F7B` → `#626673`, `availableFg` `#2E8B57` → `#1E6B43` (3,6:1 → 5,6:1), `takenFg` `#D64545` → `#B3302F` (3,6:1 → 5,1:1). La teinte reste celle de la maquette.

En sombre, `primary` devient un bleu pervenche clair avec texte bleu nuit : un bouton bleu nuit disparaîtrait sur le fond. `AccentColor` (teinte système) est aligné sur `primary`, `LaunchBackground` sur `background`.

### Typographie (`Font.Theme`)

Police système, toujours sur un style Dynamic Type (aucune taille fixe). Les deux niveaux de titre sont en **SF Pro Rounded** : la touche chaleureuse, proche du logotype de la maquette ; le reste en SF Pro pour la lisibilité.

| Token | Style système | Taille par défaut / graisse | Exemple |
|---|---|---|---|
| `largeTitle` | `.largeTitle`, rounded | 34 / Bold | « Notre famille », « Gift Manager » |
| `title` | `.title2`, rounded | 22 / Bold | « Bienvenue sur Gift Manager », prénom de l'enfant, titre d'état vide |
| `headline` | `.headline` | 17 / Semibold | Nom d'un cadeau, d'un événement, boutons |
| `body` | `.subheadline` | 15 / Regular | Textes courants |
| `callout` | `.callout` | 16 / Regular | Onglets, liens texte, lignes de boutique |
| `caption` | `.caption` | 12 / Regular | Prix, boutique, « 8 ans · Ses envies » |
| `captionBold` | `.caption` | 12 / Semibold | Badges, libellés de champs |

Prix : toujours `.monospacedDigit()`.

### Formes, espacements, profondeur

- `Spacing` : `xs` 4 · `s` 8 · `m` 12 · `l` 16 · `xl` 20 · `xxl` 32. Marge d'écran `xl`, espacement entre cartes `m`.
- `Radius` : `card` 20 · `thumb` 14 · `field` 14, toujours en `.continuous`. Boutons et badges : `Capsule`.
- `HitTarget` : `minimum` 44 (toute cible tactile) · `button` 52 (pilules pleine largeur).
- Ombre unique des cartes : noir 6 %, `y: 2`, flou 8 (`radius: 4` en SwiftUI). Pas d'autre niveau d'élévation.
- Pression : `FCPressableStyle` (échelle 0,97 + opacité 0,85, ressort 0,25 s ; pas d'échelle si Réduire les animations).

## Écrans de la maquette

| # | Écran | Éléments clés | Ticket |
|---|---|---|---|
| 1 | **Onboarding** | Illustration loutres + cadeau, titre, sous-titre, pagination à points (5 pages), bouton « Continuer » | #5 |
| 2 | **Notre famille** | Avatars empilés (+N), « 12 membres · 3 foyers », onglets Événements / Membres / Paramètres, événements à venir (icône, date, âge, avatars), événements passés, bouton + | #6, #8 |
| 3 | **Liste d'un enfant** | Avatar + prénom (sélecteur ▾), « 8 ans · Ses envies », onglets Liste (n) / Possède déjà (n) / Idées (n), cartes cadeau : vignette, nom, drapeau + prix, boutique, cœur, badge de statut | #9, #10, #11 |
| 4 | **Ajouter un cadeau** | Champ URL, aperçu (image, titre, boutique + drapeau, prix), « Ajouter à la liste », « Modifier les informations », « Autres liens pour ce cadeau » (boutique, drapeau, prix, copier) | #9 |
| 5 | **Détail du cadeau** | Grande image, cœur, titre, boutique, « Liens par pays » (drapeau, boutique, prix, lien externe), CTA vert | #11 |

**Barre d'onglets** : Accueil · Recherche · **+** (central) · Notifications · Profil. Le « + » est un disque `primary` plein dessiné (la barre ignore les palettes de SF Symbols et afficherait la variante multicolore verte), une image par apparence.

**Pagination de l'onboarding** : points SwiftUI (actif allongé 20 × 8 en `primary`, autres 8 × 8 en `textSecondary` à 40 %), à la place d'`UIPageControl` qui ne suivait pas le mode sombre. Bouton « Continuer avec Apple » noir en clair, blanc en sombre.


## API SwiftUI

Tout est dans [`ios/GiftManager/DesignSystem/`](ios/GiftManager/DesignSystem) (`Tokens/`, `Components/`). Chaque composant a des `#Preview` clair / sombre couvrant ses états. Galerie de revue (DEBUG uniquement) : `DesignSystemGallery()`.

### Tokens et modificateurs

```swift
Color.Theme.background / surface / primary / onPrimary / secondary / textPrimary / textSecondary
Color.Theme.separator / heart / accentAmber
Color.Theme.availableBg / availableFg / takenBg / takenFg / mineBg / mineFg / ownedBg / ownedFg / potBg / potFg
Color.Theme.pastelPink / pastelMint / pastelBlue / pastelPeach / pastelLavender
Color.Theme.pastel(named: String?) -> Color?   // "pastelMint" ou "mint"
Color.Theme.pastel(for seed: String) -> Color   // pastel stable dérivé d'un prénom

Font.Theme.largeTitle / title / headline / body / callout / caption / captionBold

Spacing.xs / s / m / l / xl / xxl        Radius.card / thumb / field        HitTarget.minimum / button

View.fcCard(padding: CGFloat = Spacing.l)   // fond surface, rayon card, ombre légère
View.fcScreenBackground()                    // fond background plein écran, sous les safe areas
View.fcListRow()                             // ligne de List/Form groupée : fond surface, filets separator
FCPressableStyle(pressedScale: CGFloat = 0.97)
FCSystemAppearance.apply()                   // au lancement : titres de navigation en SF Pro Rounded, textPrimary
```

### Composants

| Composant | Signature | Notes |
|---|---|---|
| `GiftStatus` | `enum GiftStatus: Equatable, CaseIterable { case available, taken, mine, owned, pot }` | `.label`, `.background`, `.foreground` |
| `StatusBadge` | `StatusBadge(status: GiftStatus)` | « Disponible », « Déjà pris », « Je l'offre », « Possède déjà », « Cagnotte » |
| `PotProgressCard` | `PotProgressCard(progress: PotProgress, myShareText: String? = nil)` | Montant réuni « sur » le prix, jauge `potFg` sur `potBg` (ressort, instantanée si Réduire les animations), participants, reste. Sans prix dans la devise de la cagnotte : pas de jauge. Pastille « Objectif atteint ». Un seul élément VoiceOver |
| `PriorityHeart` | `PriorityHeart(isOn: Bool, action: (() -> Void)? = nil)` | Bouton bascule 44 pt + haptique si `action`, indicateur sinon |
| `CountryFlag` | `CountryFlag(code: String)` | Émoji depuis ISO2 (`UK` → `GB`), repli globe ; nom du pays en français pour VoiceOver. Hérite de la police |
| `RemoteImage` | `RemoteImage(url: URL?, contentMode: ContentMode = .fill, placeholderSeed: String? = nil)` | Remplit le cadre de l'appelant. États : chargement (cadeau qui respire), absent, échec (cadeau + pastille « ! »). Placeholder : pastel stable dérivé de `placeholderSeed` (titre du cadeau ; pêche sans graine), symbole `textPrimary` à 28 %, plafonné à 48 pt |
| `GiftCard` | `GiftCard(title:imageURL:priceText:storeText:countryCode:isFavorite:status:)` | `status == nil` → **aucun badge** (mode surprise). Passe en pile verticale aux tailles d'accessibilité. Envelopper dans un `NavigationLink` |
| `StoreLinkRow` | `StoreLinkRow(store: String, countryCode: String?, priceText: String?, action: () -> Void)` | Ligne entière cliquable, trait `.isLink` |
| `ChildAvatar` | `ChildAvatar(name: String, emoji: String?, colorName: String?, size: CGFloat = 44)` | `colorName` : `pastelPink`… ; inconnu → pastel dérivé du prénom |
| `AvatarStack` | `AvatarStack(names: [String], maxVisible: Int = 5, size: CGFloat = 32)` | Chevauchement + « +N » |
| `EventKind` | `enum EventKind: Equatable, CaseIterable { case christmas, birthday, other }` | |
| `EventRow` | `EventRow(title: String, dateText: String, subtitle: String?, kind: EventKind, avatarNames: [String])` | Vignette 🎄 sur menthe, 🎂 sur rose, `gift_red` sur pêche |
| `EventKindTile` | `EventKindTile(kind: EventKind, size: CGFloat = 56)` | Vignette seule (en-têtes d'événement) |
| `PrimaryButton` | `PrimaryButton(title: String, systemImage: String? = nil, isLoading: Bool = false, action: () -> Void)` | Pilule bleu nuit pleine largeur. `chevron.*` / `arrow.*` se calent à droite, les autres icônes à gauche. `.disabled(true)` → 45 % d'opacité |
| `SecondaryButton` | même signature | Pilule verte `secondary` |
| `TextLinkButton` | `TextLinkButton(title: String, action: () -> Void)` | Lien texte `primary`, cible 44 pt |
| `FCPillLabel` | `FCPillLabel(title: String, systemImage: String? = nil, style: .primary \| .secondary = .primary)` | Apparence pilule pour les contrôles qui ne sont pas des `Button` (`ShareLink`) ; poser `.buttonStyle(FCPressableStyle())` sur l'hôte |
| `FCNotice` | `FCNotice(systemImage: String, text: String, tone: .surprise \| .neutral \| .warning = .neutral)` | Encart icône + phrase. `.surprise` lavande (`ownedBg`/`ownedFg`) pour le mode surprise, `.neutral` carte bordée (archive, précisions), `.warning` rose (`takenBg`/`takenFg`) |
| `SegmentedTabs` | `SegmentedTabs(selection: Binding<Int>, titles: [String])` | Soulignement animé ; défilement horizontal si les libellés ne tiennent pas |
| `EmptyStateView` | `EmptyStateView(imageName: String, title: String, message: String, actionTitle: String? = nil, action: (() -> Void)? = nil)` | Mascotte d'`Illustrations.xcassets` |
| `FeaturePill` | `FeaturePill(systemImage: String, color: Color, title: String, subtitle: String, background: Color? = nil)` | `color` teinte l'icône ; `background` = pastel du disque (sinon teinte de `color` à 16 %) |
| `SectionHeader` | `SectionHeader(title: String, actionSystemImage: String? = nil, action: (() -> Void)? = nil, actionLabel: String? = nil)` | Bouton rond `primary` ; `actionLabel` pour VoiceOver (« Ajouter » par défaut) |
| `FCTextField` | `FCTextField(title: String, text: Binding<String>, systemImage: String? = nil, prompt: String? = nil, isTitleHidden: Bool = false)` | Libellé visible au-dessus ; `isTitleHidden` le garde pour VoiceOver seulement. Bouton « Effacer » au focus |
| `ApproxPriceText` | `ApproxPriceText(text: String)` | Conversion indicative « ≈ 214 € » posée **après** le prix d'origine, toujours `textSecondary`, chiffres tabulaires ; VoiceOver : « environ 214 €, conversion indicative ». Hérite de la police |
| `IndicativeRateNote` | `IndicativeRateNote(rateDateText: String?)` | Mention « Conversion indicative, taux BCE du 2 octobre. Le prix de la boutique fait foi. » sous des prix convertis |
| `DesignSystemGallery` | `DesignSystemGallery()` | `#if DEBUG` |

### Règles d'usage

- Un seul `PrimaryButton` par écran ; `SecondaryButton` pour le CTA vert de la fiche cadeau.
- Pas de pilule grisée en permanence : un bouton « Enregistrer / Renommer » qui n'a de sens qu'après modification apparaît quand le champ change (Profil, nom de la famille). Les formulaires longs (ajout de cadeau) gardent leur CTA en bas d'écran, désactivé tant que le nom manque.
- Tout champ a un libellé visible (`FCTextField`, ou libellé `captionBold` au-dessus) ; les invites (`prompt`) sont en `textSecondary`, jamais le gris tertiaire système (contraste insuffisant).
- Rappels de mode surprise, d'archive ou d'alerte : `FCNotice`, pas une ligne de légende grise isolée.
- Listes `List` / `Form` : `.scrollContentBackground(.hidden)` + `.fcScreenBackground()`, lignes `.fcListRow()` en groupé ; en `.plain`, cartes posées sur `listRowBackground(.clear)`, séparateurs masqués, encarts `Spacing.xl` horizontaux et `Spacing.xs` verticaux.
- Rouge destructif en texte : `takenFg` (le rouge système fait 3,5:1 sur blanc).
- États de chargement : `ProgressView` titré (« Chargement de … ») teinté `primary`, jamais d'écran blanc avant le premier chargement.
- Éléments archivés (événements passés) : vignette désaturée (`grayscale`), jamais d'opacité sur le texte.
- Avatar à côté du prénom écrit : `accessibilityHidden(true)` sur l'avatar, sinon VoiceOver lit le prénom deux fois.
- Les icônes sont des SF Symbols ; les seuls émojis sont les drapeaux, les vignettes d'événement et les avatars choisis par la famille.
- Toute icône seule porte un `accessibilityLabel` ; les cartes regroupent leur contenu en un seul élément VoiceOver.
- Mode surprise : ne jamais calculer un `GiftStatus` pour un parent qui regarde la liste de son enfant ; passer `nil`. Idem pour les listes d'adultes de son foyer (#37).
- Cagnotte (#35) : progression visible des non-parents ; noms et montants des participants seulement pour un participant (« Moi » pour soi). Toujours rappeler que les autres participants voient le prénom et la part.
- Équilibre (#41) : une ligne `caption` sous la liste, `textSecondary` ; ambre (`accentAmber`) quand rien n'est prévu. Jamais sur mes propres listes.
- Remerciements (#42) : un donateur n'est nommé aux parents que s'il s'est dévoilé (« Offert par … ») ; sinon « la personne qui l'a offert ». L'expéditeur d'un merci (un parent) est nommé côté donateur.
- Conversion de devises (#38) : uniquement à côté d'un prix dans une autre devise que celle du profil, jamais à sa place. Format « prix d'origine ≈ montant converti », arrondi à l'unité (au centime sous 10). Là où plusieurs prix sont convertis (fiche cadeau, total de Mes achats), le mot « indicatif » est écrit en toutes lettres. Taux BCE du jour via `CurrencyService.shared` ; hors ligne, dernier taux connu ; sans aucun taux, rien n'est affiché.

## Écarts avec les spécifications — décisions

1. **Badges de statut et mode surprise** — L'écran 3 montre « Disponible » / « Déjà pris ». C'est la vue **d'un autre membre de la famille**. Quand un parent regarde la liste de son propre enfant, `StatusBadge` est masqué (aucun badge). Règle non négociable (cf. `docs/SPEC.md`).
2. **Onglet « Idées »** — Validé : idées de cadeaux suggérées par les adultes de la famille, visibles des autres membres mais **pas des parents de l'enfant** tant qu'elles ne sont pas ajoutées à la liste (ticket dédié).
3. **Note et avis (★ 4.8, 128 avis)** — Non récupérables de façon fiable depuis un lien (Amazon, Galaxus bloquent). Affichés seulement si l'aperçu les fournit, sinon masqués. Pas de saisie manuelle.
4. **Onglets Recherche et Notifications** — Recherche : recherche dans les listes du groupe (v1). Notifications : liées au ticket push (#15, v1.1) ; en v1, l'onglet affiche l'activité récente du groupe (sans jamais révéler qui a réservé).

## Illustrations

Catalogue d'assets : [`ios/GiftManager/Resources/Illustrations.xcassets`](ios/GiftManager/Resources/Illustrations.xcassets). Usage SwiftUI : `Image("mascot_gift")`.

### Validés

| Asset | Usage prévu |
|---|---|
| `mascot_family` | Onboarding page 1, écran « Notre famille » vide |
| `mascot_gift` | Onboarding (cadeaux), succès d'ajout |
| `mascot_surprise` | Onboarding (surprise garantie), écran « mode surprise » |
| `mascot_celebrate` | Confirmation de réservation, « Acheté » |
| `mascot_love` | Cadeau « très envie », onboarding |
| `mascot_thinking` | Onglet Idées vide |
| `mascot_sleeping` | Aucune notification / activité |
| `gift_red` | Événement Noël, icône générique de cadeau |
| `empty_box` | Liste de souhaits vide |
| `illustration_travel` | Onboarding (plusieurs pays) |

Dans le design system : `gift_red` illustre `EventRow(kind: .other)` ; `EmptyStateView(imageName:)` accepte n'importe lequel de ces assets. Tant que `illustration_birthday` et une illustration de sapin ne sont pas refaites, les vignettes Anniversaire et Noël utilisent 🎂 et 🎄 sur pastel (`EventKindTile`).

### À refaire (exports défectueux)

Ces fichiers ont été mal découpés depuis la planche d'origine : nom décalé par rapport au contenu et/ou morceaux de l'illustration voisine sur les bords. Ils **ne sont pas** dans le dépôt.

| Fichier livré | Contenu réel | Problème |
|---|---|---|
| `empty_calendar` | Carton + morceau flou | Pas de calendrier, fragment voisin |
| `empty_error` | Loupe + document | Correspond à « recherche » |
| `empty_lock` | Wi-Fi barré | Correspond à « hors ligne » |
| `empty_offline` | Bulle de dialogue | Ni hors ligne ni erreur |
| `empty_search` | Point d'interrogation + phoque gris | Fragment + style différent des loutres |
| `gift_blue` | Cadeau jaune + fragment bleu | Couleur et fragment |
| `gift_gold` | Coche verte | Pas un cadeau |
| `gift_green` / `gift_purple` | Cadeau + fragment voisin | Fragment sur le bord |
| `illustration_birthday` | Calendrier + gâteau + fragment rose | Fragment en haut |
| `illustration_gift_ideas` | Jouets + cadeau | Halo flou au centre |
| `illustration_security` | Checklist | Pas de sécurité |
| `illustration_shopping` | Bouclier + cadenas + sac + téléphone | Sécurité et shopping fusionnés |

### Résolution

Les PNG sont livrés en **@1x uniquement** (77 à 408 px de large). Sur iPhone (@3x), une illustration affichée à 260 pt demande ~780 px : en l'état elles seront floues sur l'onboarding. À fournir idéalement en **@3x (≥ 900 px de large pour les mascottes)**, ou en PDF vectoriel.

### Manquant

- **Icône d'app** 1024 × 1024 (cadeau 3D de la maquette), sans transparence.
