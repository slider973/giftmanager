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
| `accentAmber` | `#B85F0E` | `#F2A65A` | Accent chaud (pastille « Surprise », placeholder d'image) | 4,1 / 6,9 sur `background` — voir [Android](#android-compose) |
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

## Android (Compose)

Port du design system dans `android/app/src/main/kotlin/ch/jonathanlemaine/giftmanager/ui/` (`theme/`, `components/`). Mêmes tokens, mêmes composants, mêmes règles d'usage qu'iOS ; les écarts ci-dessous sont propres à Android.

### Tokens

- **Couleurs** : valeurs ARGB dans `ThemeTokens.Light` / `ThemeTokens.Dark` (`PaletteTokens.kt`), vérifiées contre les `.colorset` iOS par `PaletteParityTest`. Résolues en `GiftColors` immuable (`Color.kt`), accès `GiftTheme.colors.primary`… `pastel(Pastel)`, `pastel(named)` et `pastelFor(seed)` reprennent `Color.Theme.pastel(named:)` / `pastel(for:)`. **Pas de couleurs dynamiques Material You** : la palette est celle de la marque.
- **Typographie** (`Type.kt`, `GiftTheme.type`) : mêmes 7 styles et mêmes tailles qu'iOS, **toujours en `sp`** (suivent la taille de police système). `largeTitle` et `title` en **Nunito** (police variable, axe `wght`, licence OFL dans `android/licenses/`) : l'équivalent de SF Pro Rounded. Le reste en police système (Roboto). Prix et montants : `style.tabular()` (`fontFeatureSettings = "tnum"`).
- **Mesures** (`Metrics.kt`) : `GiftTheme.spacing` (xs 4 · s 8 · m 12 · l 16 · xl 20 · xxl 32 dp), `GiftTheme.radius` (card 20 · thumb 14 · field 14 dp, + `cardShape`…), `CapsuleShape` pour boutons et badges. `HitTarget.minimum` = **48 dp** (Material) au lieu de 44 pt, `HitTarget.button` = 52 dp. Les mesures qui suivent Dynamic Type sur iOS (`@ScaledMetric`) grandissent avec l'échelle de police, plafonnées.
- **Profondeur et pression** (`Modifiers.kt`) : `Modifier.fcCard(padding)` (fond `surface`, rayon `card`, ombre unique noir 6 %, y 2 dp, flou 8 dp), `fcScreenBackground()`, `fcPressable(interactionSource)` et `fcClickable(onClick)` (échelle 0,97 + opacité 0,85, ressort 0,25 s ; pas d'échelle si animations réduites ; **anneau `primary` de 2 dp au focus clavier / D-pad**, puisque ces contrôles n'ont pas d'ondulation). `fcListRow` n'a pas d'équivalent : pas de `List` groupée en Compose.
- **Animations réduites** : `rememberReduceMotion()` lit `Settings.Global.ANIMATOR_DURATION_SCALE` (0 = animations coupées, réglage « Supprimer les animations ») et suit les changements en direct ; exposé par `GiftTheme.reduceMotion`.

### Projection Material 3

`GiftManagerTheme` fournit les tokens **et** un `ColorScheme` / `Typography` M3, pour que les composants Material (Switch, Slider, indicateurs, sélection de texte) prennent l'identité de l'app.

| Rôle M3 | Token | | Rôle M3 | Token |
|---|---|---|---|---|
| `background` | `background` | | `primary` / `onPrimary` | `primary` / `onPrimary` |
| `surface`, `surfaceContainer`, `…High`, `…Highest`, `surfaceBright` | `surface` | | `secondary` / `onSecondary` | `secondary` / `onPrimary` |
| `surfaceVariant`, `surfaceContainerLow`, `…Lowest`, `surfaceDim` | `background` | | `tertiary` / `onTertiary` | `accentAmber` / `onPrimary` |
| `onBackground`, `onSurface`, `inverseSurface` | `textPrimary` | | `primaryContainer` / on | `mineBg` / `mineFg` |
| `onSurfaceVariant`, `outline` | `textSecondary` | | `secondaryContainer` / on | `availableBg` / `availableFg` |
| `outlineVariant` | `separator` | | `tertiaryContainer` / on | `potBg` / `potFg` |
| `surfaceTint` | `surface` (teinte d'élévation neutralisée) | | `error` / `onError` | `takenFg` / `onPrimary` |
| `scrim` | noir | | `errorContainer` / on | `takenBg` / `takenFg` |

Typographie : `headlineLarge` / `displaySmall` = `largeTitle`, `titleLarge` / `headline*` = `title`, `titleMedium` / `labelLarge` = `headline`, `bodyLarge` = `callout`, `bodyMedium` = `body`, `bodySmall` / `labelSmall` = `caption`, `labelMedium` = `captionBold`. Paires texte des rôles vérifiées AA par `MaterialRolesContrastTest`.

La fenêtre native (`res/values[-night]/themes.xml`) prend la couleur `background` pour éviter un flash au lancement ; l'app est bord à bord (`enableEdgeToEdge`), chaque écran pose les marges `WindowInsets.safeDrawing`.

### Écart : `accentAmber` en clair

`accentAmber` clair (`#B85F0E`) ne fait que **4,11:1 sur `background`** (4,49:1 sur `surface`, 4,49:1 sous un texte blanc), et non 4,5 comme annoncé dans le tableau des couleurs. La valeur n'est **pas** modifiée (parité avec iOS) ; sur Android :

- jamais de **texte** `accentAmber` sur `background` ou `surface`, ni de texte posé sur un fond `accentAmber` (rôle M3 `tertiary`) ;
- usages permis : formes et icônes (≥ 3:1 : étincelles de `GiftLoadingView`, icône de `FeaturePill` sur `pastelPeach`, jauge), toujours doublées d'un texte ;
- les usages iOS en texte (« ambre quand rien n'est prévu » de l'équilibre #41, niveau « Plus que 20 CHF » des budgets #40) passeront en `potFg` sur Android.

`ContrastTest` et `MaterialRolesContrastTest` figent cet écart (3:1 exigé en clair, AA en sombre). À corriger d'abord côté iOS si l'ambre doit un jour porter du texte seul.

### Composants

Un fichier par composant dans `ui/components/`, chacun avec `modifier: Modifier = Modifier` et des aperçus `@GiftPreviews` (clair, sombre, police 200 %). Libellés en français dans `res/values/strings.xml`. Signatures alignées sur SwiftUI, aux idiomes Compose près :

| iOS | Compose | Différences |
|---|---|---|
| `StatusBadge(status:)` | `StatusBadge(status)` | Libellé par `GiftStatus.labelRes` ; couleurs `status.background(colors)` / `foreground(colors)` |
| `GiftCard(...)` | `GiftCard(title, imageUrl, priceText, storeText, countryCode, isFavorite, status, approxPriceText, onClick, trailingAction)` | `onClick` remplace le `NavigationLink`. Pile verticale dès `fontScale ≥ 1,5`. `trailingAction` : petite action en bas à droite (menu ⋮ d'une ligne, #73), nœud TalkBack à part |
| `PotProgressCard(progress:myShareText:)` | `PotProgressCard(progress, myShareText)` | Un seul nœud TalkBack |
| `PriorityHeart(isOn:action:)` | `PriorityHeart(isOn, onToggle: ((Boolean) -> Unit)?)` | Rôle interrupteur, haptique `ToggleOn/Off` |
| `CountryFlag(code:)` | `CountryFlag(code, style)` | Logique dans `CountryFlagLogic` |
| `RemoteImage(url:contentMode:placeholderSeed:)` | `RemoteImage(url, contentScale, placeholderSeed, contentDescription)` | Coil 3 ; `contentDescription = null` = décorative |
| `StoreLinkRow(store:countryCode:priceText:action:)` | `StoreLinkRow(store, countryCode, priceText, onClick, approxPriceText)` | TalkBack : « Appuyez deux fois pour ouvrir le site de la boutique » |
| `ChildAvatar(name:emoji:colorName:size:)` | `ChildAvatar(name, emoji, colorName, size, photoUrl)` | Photo optionnelle (Coil), avatar pastel en repli |
| `AvatarStack(names:maxVisible:size:)` | `AvatarStack(names, maxVisible, size)` | |
| `EventRow(...)` / `EventKindTile(kind:size:)` | `EventRow(title, dateText, subtitle, kind: GiftEventKind, avatarNames, onClick)` / `EventKindTile(kind, size)` | `GiftEventKind` du modèle au lieu d'un `EventKind` d'UI |
| `PrimaryButton` / `SecondaryButton(title:systemImage:isLoading:action:)` | `PrimaryButton(text, onClick, leadingIcon, trailingIcon, isLoading, enabled)` | Icône de tête ou de queue explicite (iOS : déduit du nom `chevron.*`/`arrow.*`) ; `enabled` remplace `.disabled()` |
| `TextLinkButton(title:action:)` | `TextLinkButton(text, onClick)` | |
| `FCPillLabel(title:systemImage:style:)` | `FCPillLabel(text, icon, style: FCPillStyle)` | L'hôte pose `fcClickable` |
| `FCNotice(systemImage:text:tone:)` | `FCNotice(icon, text, tone: FCNoticeTone)` | |
| `SegmentedTabs(selection:titles:)` | `SegmentedTabs(selectedIndex, onSelect, titles)` | `Role.Tab`, position « 1 sur 3 » |
| `EmptyStateView(imageName:...)` | `EmptyStateView(illustration: Illustration, title, message, actionLabel, onAction)` | |
| `FeaturePill(systemImage:color:title:subtitle:background:)` | `FeaturePill(icon, color, title, subtitle, background)` | |
| `SectionHeader(title:actionSystemImage:action:actionLabel:)` | `SectionHeader(title, actionIcon, onAction, actionLabel)` | Titre = `heading()` TalkBack |
| `FCTextField(title:text:systemImage:prompt:isTitleHidden:)` | `FCTextField(label, value, onValueChange, leadingIcon, placeholder, isLabelHidden, keyboardOptions)` | Libellé dans la décoration du champ (lu avec la valeur) |
| `ApproxPriceText(text:)` / `IndicativeRateNote(rateDateText:)` | idem | |
| `GiftLoadingView(size:label:)` | `GiftLoadingView(size, label)` | Port fidèle : une seule `rememberInfiniteTransition`, valeurs lues au dessin ; figée (paquet droit, étincelles visibles) si animations réduites ; région live « Chargement en cours » |

**Icônes** : `GiftIcons` — `material-icons-core` (cœur, plus, chevron, retour, fermer, recherche, cadenas, partage, alerte) et vector drawables Material (`res/drawable/ic_*.xml` : cadeau, sac, globe, lien externe, groupe, œil barré, archive, lien, erreur, bouclier, effacer, sablier). Pas d'`icons-extended`.

**Illustrations** : `Illustration` (enum) → `res/drawable-nodpi/*.webp`, générés depuis `Illustrations.xcassets` par `scripts/android/convert-illustrations.sh` (WebP sans perte avec alpha, idempotent, nécessite `cwebp`). Relancer le script après toute mise à jour des PNG iOS.

### Galerie (debug)

`DesignSystemGallery` vit **uniquement** dans `android/app/src/debug/kotlin/…/ui/debug/` ; `src/release` fournit un `DebugEntryPoints` vide de même API, si bien que le build release ne contient aucune classe de la galerie. Accès : ligne « Galerie du design system » de l'écran Profil, sous « Se déconnecter » (debug seulement). Sections dans l'ordre iOS, plus la cagnotte ; barre fixe avec bascule clair / sombre et curseur de taille du texte 100 → 200 % pour éprouver les grandes polices sans changer les réglages du téléphone.

### Connexion Google (#70)

- **Écran** (`ui/auth/SignInScreen.kt`) : la dernière page de l'onboarding iOS — `mascot_surprise` (280 dp au plus, réduite à 40 % de la hauteur en paysage), « La surprise est garantie » en `title`, message en `body` / `textSecondary` (320 dp de large), page défilable ; bouton Google fixé en bas, marge `xl`. Contenu réutilisable par l'onboarding (#71) : `OnboardingPage` (une page du carrousel) et `SignInActions` (zone d'action), assemblés par `SignInContent`.
- **Bouton** (`GoogleSignInButton`) : règles de marque « Sign in with Google », variante Android. Pilule de `HitTarget.button`, logo « G » officiel (`res/drawable/ic_google_g.xml`, jamais teinté), libellé « Se connecter avec Google » en police système Medium (`callout`, 16 sp plutôt que les 14 sp de Google, pour la lisibilité). Clair : fond `#FFFFFF`, bordure 1 dp `#747775`, texte `#1F1F1F` ; sombre : `#131314` / `#8E918F` / `#E3E3E3`. **Seule exception aux tokens** : couleurs imposées par Google, isolées dans `GoogleButtonColors` et le drawable, vérifiées par `GoogleSignInButtonContrastTest`. Le thème suit l'app (`GiftTheme.colors.isDark`). En cours : désactivé, le logo cède sa place à un indicateur de même taille, libellé à 60 %, TalkBack annonce « Connexion en cours ».
- **Erreurs** : `AlertDialog` « Oups » (comme l'alerte iOS), message selon la cause, « OK ». Annulation : rien.
- **Déconnexion** : écran Profil (#71) ; pendant la configuration de la famille, menu « Options » en haut à droite.

### Onboarding, profil et foyer (#71)

Écrans dans `ui/onboarding`, `ui/family`, `ui/profile`, `ui/main` ; briques partagées dans `ui/common` (`ScreenColumn`, `GiftAlertDialog` / `GiftErrorDialog`, `SettingsSection` / `SettingsRow` / `ToggleRow` / `PickerRow`, `ProfileFields`). Textes, hiérarchie et comportements repris des vues SwiftUI correspondantes.

- **Colonne d'écran** (`ScreenColumn`) : fond `background`, marges `safeDrawing` **hors** du défilement (rien sous la barre d'état, le contenu remonte au-dessus du clavier), contenu limité à 560 dp et centré (tablette, paysage), marge `xl`.
- **Onboarding** : `HorizontalPager` de 4 pages (`OnboardingPage`, illustration ≤ 280 dp et ≤ 40 % de la hauteur), points de pagination (actif 20 × 8 `primary`, autres 8 × 8 `textSecondary` 40 %, ressort ; un seul nœud TalkBack « Page 2 sur 4 »). « Continuer › » sur les trois premières, bouton Google sur la dernière : même place, même hauteur, fondu de 200 ms. Retour système = page précédente ; carrousel figé pendant la connexion.
- **Configuration du profil** : `mascot_love` 160 dp, « Faisons connaissance » (`largeTitle`), `ProfileFields`, « C'est parti → ».
- **`ProfileFields`** : prénom en `FCTextField` (majuscule initiale, saisie automatique « prénom ») ; pays et devise dans une carte à deux lignes de choix (`PickerRow` : icône, libellé, valeur `primary` + ▾, menu calé à droite, option cochée en `secondary`, rôle « liste déroulante »). Écart iOS : la devise est un menu et non un contrôle segmenté — cinq segments ne tiennent pas sur 360 dp aux grandes polices, et le menu est l'idiome Android.
- **Famille (configuration)** : quatre sous-écrans en fondu + glissement d'un huitième de largeur dans le sens de la navigation (instantané si animations réduites). Codes d'invitation et de foyer : majuscules, sans correction, clavier ASCII, « OK » valide. Étape foyer : trois cartes (partager le foyer existant, créer, rejoindre celui du conjoint) ; seul le bouton touché affiche l'indicateur. « Se déconnecter » dans le menu « Options » (⋮, 48 dp), en `takenFg`.
- **Notre famille** : grand titre = nom de la famille, `AvatarStack` 40 dp, « N membres · M foyers » ; `SegmentedTabs`. Membres : une carte par foyer (drapeau, nom en titre TalkBack, badge « Mon foyer » `availableBg` / `availableFg`, parents), et pour mon foyer la ligne « Code du foyer pour mon conjoint : **CODE** » (chasse fixe) qui ouvre le partage. Paramètres : code d'invitation en `largeTitle` chasse fixe, interlettrage 4 sp, `primary` sur `background`, sélectionnable, lu lettre par lettre ; « Partager l'invitation » (`FCPillLabel`) ; « Renommer » qui n'apparaît qu'après modification ; « Mes familles » (coche `secondary`, rôle bouton radio, « sélectionné ») ; « Rejoindre une autre famille » en dialogue. Tirer pour actualiser : indicateur `primary` sur `surface`. Événements et listes des foyers : voir #72.
- **Profil** : liste groupée à la manière iOS — titre de section `captionBold` `textSecondary` (titre TalkBack), carte `surface` sans marge interne, lignes ≥ 56 dp séparées par des filets `separator` alignés sur le texte, note de bas `caption`. En-tête : `ChildAvatar` 64 dp + prénom + foyer, un seul nœud TalkBack. « Enregistrer » n'apparaît qu'en cas de changement. Interrupteurs : toute la ligne bascule (`Role.Switch`), `Switch` M3 aux couleurs `primary` / `onPrimary`, piste éteinte `background` bordée `textSecondary`. « Retirer » et « Supprimer mon compte » en `takenFg`, ce dernier seul dans sa carte, tout en bas ; confirmations en `GiftAlertDialog` destructive. Version `BuildConfig` en pied de page.
- **Alertes** (`GiftAlertDialog`) : carte `surface` rayon `card`, titre `title`, message `callout` / `textSecondary`, boutons texte `primary` (action destructive en `takenFg` semi-gras), corps défilant aux grandes polices. « Oups » (`GiftErrorDialog`) en est un cas particulier, identique à l'alerte de connexion (#70).
- **Échec de chargement** : `EmptyStateView` `mascot_thinking`, « Chargement impossible », « Réessayer » en CTA, « Se déconnecter » en lien.

### Barre d'onglets (#71)

`MainNavigationBar` : `NavigationBar` M3 sur `surface`, filet `separator` au-dessus, cinq cases dans l'ordre iOS — **Accueil · Recherche · « + » · Mes achats · Profil**.

- Onglet actif : icône et libellé `primary`, pastille `primary` à 12 % ; inactifs : `textSecondary` (5,3:1 en clair). Icônes pleines, `material-icons-core` + vecteurs maison (`ic_shopping_bag`), sans `icons-extended`.
- **« + » central** : disque `primary` plein de 48 dp, croix `onPrimary` 28 dp, sans libellé ni état sélectionné ; toute la case (≥ 48 dp) est la cible, le disque s'enfonce à la pression (0,9, coupé si animations réduites). TalkBack : « Ajouter un cadeau, bouton ». Il ouvre une feuille (provisoire « Bientôt ici » jusqu'à #73), jamais une destination.
- **« Mes achats » en 4e position**, comme l'app iOS livrée. Écart assumé avec le tableau de la maquette (« Notifications » à cet emplacement, cf. [Écrans de la maquette](#écrans-de-la-maquette)) : les notifications push arrivent avec #76 et leurs réglages vivent dans Profil.
- Recherche et Mes achats affichent `ComingSoonScreen(illustration, titre, message)` (grand titre + état vide) en attendant leurs tickets ; mêmes loutres que les états vides iOS (`mascot_thinking`, `mascot_sleeping`).
- Bord à bord : le `Scaffold` réserve barre d'état et barre d'onglets puis **consomme** ces marges ; les écrans posent `safeDrawing` sans double comptage (seul le clavier reste à leur charge).

### Enfants et événements (#72)

Écrans dans `ui/children` (`ChildEditorScreen`, `ChildRow`, `ChildGiftsPlaceholder`) et `ui/events` (`EventEditorScreen`, `EventDetailScreen`) ; briques partagées dans `ui/common` (`GiftTopBar`, `TopBarScreen`, `DateField`). Textes et règles repris de `ChildEditorView`, `EventEditorView`, `EventDetailView` et `FamilyHomeView`.

- **Barre du haut** (`GiftTopBar`, à la manière d'un `TopAppBar` M3 « small ») : fond `background`, sans ombre ; ← « Retour » (écran poussé) ou ✕ « Annuler » (formulaire) en 48 dp, titre `title` sur deux lignes au plus (titre TalkBack), actions texte `primary` (« Modifier »). Filet `separator` qui apparaît en fondu dès que le contenu défile dessous. `TopBarScreen` la fixe hors du défilement : le retour reste toujours accessible.
- **Barre d'onglets** : **masquée sur les formulaires plein écran** (éditeurs d'enfant et d'événement — idiome Material du *full-screen dialog* : on en sort par ✕ ou Retour, pas en changeant d'onglet), **visible sur les écrans poussés de consultation** (fiche d'un événement, liste d'un enfant), comme la pile de navigation iOS. Repli / dépli vertical de 200 ms, instantané si animations réduites.
- **`DateField`** : même habit que `FCTextField` (libellé `captionBold`, champ `surface` bordé, icône calendrier, date longue, ▾ `primary`) ; un seul bouton TalkBack (« Née / né le, 12 mars 2019, appuyez deux fois pour choisir une date »). Ouvre le `DatePickerDialog` M3 : carte `surface` rayon `card`, jour choisi en disque `primary`, aujourd'hui cerclé `primary`, mois et jours en français ; `max` grise jours et années au-delà (naissance : aujourd'hui). S'ouvre en saisie clavier si la fenêtre fait moins de 600 dp de haut ou si la police dépasse 150 % (le calendrier ne tiendrait pas).
- **Éditeur d'enfant / de liste d'adulte** : avatar 96 dp qui suit la saisie (rebond au changement d'émoji ou de couleur, fondu seul si animations réduites ; décoratif pour TalkBack), prénom (« Nom affiché » pour un adulte, saisie automatique « prénom »), `FCNotice` surprise pour un adulte, carte « Date de naissance » (`ToggleRow` + `DateField` qui se déplie), carte « Avatar » : grille de 6 colonnes au plus (moins sur écran étroit, cases ≥ 48 dp), choix = disque de la couleur choisie + anneau `primary` 2 dp ; pastilles 36 dp dans 48 dp, anneau `primary` 2,5 dp autour du choix. Deux groupes de boutons radio (« Avatar 🦊, sélectionné », « Couleur menthe »). CTA « Ajouter » / « Créer la liste » / « Enregistrer » ; « Supprimer Emma » en `takenFg` dans **sa propre carte**, à l'écart du CTA, confirmation destructive.
- **Éditeur d'événement** : titre (80 caractères ; compteur « 72 / 80 » aligné à droite à partir de 64, `potFg` semi-gras à la limite), date, « Enregistrer » ; suppression dans sa carte avec la note « Les cadeaux restent dans les listes des enfants. ». Écart iOS : pas de choix du type (Noël est global, les anniversaires sont créés par le serveur).
- **Fiche d'un événement** : grand titre (repris dans la barre en fondu une fois passé dessous), en-tête vignette + date + compte à rebours / « Événement passé » + « Léo fête ses 8 ans » (un seul nœud TalkBack), encart d'archive si passé, sections « Les listes des enfants », « Les listes des adultes », « Mes enfants » / « Mon foyer » + `FCNotice` surprise. Carte de liste : `ChildRow` (avatar 40 dp, prénom, âge ou « Liste d'adulte » · foyer), chevron, indicateur d'équilibre aligné sur le prénom — 🎁 « 2 réservés · 1 acheté » en `textSecondary`, ⏳ « Rien de prévu pour l'instant » en **`potFg`** medium (l'« ambre doux » iOS, cf. [écart `accentAmber`](#écart--accentamber-en-clair)) ; TalkBack « Équilibre : … ». Tirer pour actualiser. État vide `empty_box` « Aucune liste ».
- **Notre famille › Événements** : `EventRow` ; sous-titre « Dans 12 jours · 8 ans ». « Modifier » par **appui long** (menu, retour haptique) sur un événement modifiable, comme le menu contextuel iOS, et en action TalkBack ; aussi depuis la fiche. « Événements passés » **désaturés** (`fcDesaturated`, saturation 0,3 ; texte à pleine opacité pour rester AA).
- **Notre famille › Membres** : les listes de chaque foyer en `ChildRow` (le foyer n'y est pas répété, la carte le dit) ; pour mon foyer, crayon `primary` sur disque `background` (cible 48 dp, « Modifier Léo »), puis « Ajouter un enfant » et « Créer une liste d'adulte » avec sa précision visible en légende (iOS : simple indice VoiceOver).
- **Liste d'un enfant** : voir [Cadeaux, idées et cagnottes (#73)](#cadeaux-idées-et-cagnottes-73).

### Cadeaux, idées et cagnottes (#73)

Écrans dans `ui/gifts` (briques communes : `GiftParts.kt` — `GiftActionsMenu`, `RoundIconButton`, `PrivacyCaption`, `FieldLabel`, `openInBrowser` ; données d'aperçu : `GiftPreviewData`). Textes, hiérarchie et comportements repris de `ChildGiftsView`, `GiftDetailView`, `AddGiftView`, `JoinPotView`, `PendingIdeasView` et `QuickAddGiftView`. **Confidentialité** : tout statut, badge, bouton de réservation ou de cagnotte vient de l'état des ViewModels (`isParent` / `isParentView`, `reservation: ReservationActions?`, `GiftActionReducer.badge`) ; l'interface ne recalcule jamais un statut. Vue parent : aucun badge, notice « Mode surprise ».

- **Liste d'un enfant** : `LazyColumn` (560 dp au plus), en-tête avatar 56 dp + prénom (`title`) + « 8 ans · Ses envies » / foyer (un nœud, titre TalkBack) ; prénom repris dans la barre en fondu une fois l'en-tête passé dessous. `SegmentedTabs` avec compteurs (Idées jamais pour les parents), pastille « événement affiché » (capsule `surface`, calendrier + titre + ▾ `primary`, menu coché, rôle liste déroulante). États vides par onglet (`empty_box`, `mascot_gift`, `mascot_thinking`) avec l'action d'ajout quand elle existe ; chargement `GiftLoadingView`. Événement passé : « Archivé » dans la barre + `FCNotice`. **Actions de carte** (iOS : balayages) : ⋮ (`trailingAction`), appui long avec retour haptique, et actions TalkBack — « Très envie » / « Retirer le cœur » (cœur `heart`), « Il l'a déjà » / « Remettre dans la liste » (`secondary`), « Supprimer » (`takenFg` semi-gras, confirmation destructive). **Ordonner** : ↑ / ↓ de 48 dp à droite de chaque souhait (désactivés en bout de liste), « Position 2 sur 5 » lu par TalkBack, « Monter » / « Descendre » aussi en actions TalkBack hors de ce mode ; placement animé (ressort), instantané si animations réduites. Barre : `AddCircle` « Ajouter un cadeau » (ou « …déjà possédé »), ampoule « Proposer une idée ».
- **Fiche cadeau** : image 280 dp (180 sans photo) en `fcCard`, cœur sur pastille `surface` (interrupteur pour les parents seulement), bandeau d'idée (4 verdicts ; refusée en `takenFg`, sinon **`potFg`** et non l'ambre iOS), titre, `StatusBadge`, notes, « Liens par pays » (`StoreLinkRow`, navigateur par `Intent.ACTION_VIEW`). Actions : disponible → « Je l'offre » (`SecondaryButton`) + « Participer à une cagnotte » + mention cadenas ; cagnotte → `PotProgressCard`, participants (carte, « Moi » en gras, parts tabulaires), « Modifier ma part » / « Quitter la cagnotte » (confirmation) ou « Participer à la cagnotte » ; je l'offre → « C'est acheté » ou pastille « Acheté — bravo ! » (`availableBg` / `availableFg`), « Annuler ma réservation » ; pris / possédé → `FCNotice`. Menu ⋮ : « Je l'offre moi-même » (parent), « Modifier », « Il l'a déjà », « Supprimer ». Célébration : carte `mascot_celebrate` au centre, ressort + fondu (fondu seul si animations réduites), 1,8 s ou toucher, région live.
- **Ajout** : champ de lien + bouton « Coller » (disque `primary`, lance l'aperçu), carte d'aperçu (cadre 200 dp bordé, pastille « Recherche de l'aperçu… », bouton « Photo » = **Photo Picker** `PickVisualMedia.ImageOnly`, sans autorisation ; JPEG redimensionné par `AndroidImageResizer` sur IO), nom, boutique + drapeau, prix tabulaire + devise en menu (empilés dès 150 %). Options en lignes de réglage (`ToggleRow` avec `iconTint`, `PickerRow` « Pour ») et texte d'audience de l'idée (`takenFg` si personne ne la verra). « Autres liens » + feuille « Autre lien ». Bouton d'enregistrement fixé en bas, au-dessus de la barre de navigation et du clavier. Doublon : « Ce cadeau existe déjà » (événement nommé).
- **Cagnotte** (feuille) : en-tête cadeau, `PotProgressCard`, montant en `title` tabulaire dans un champ `surface` (bordure `primary` au focus, `takenFg` + message si illisible), devise verrouillée ou en menu, suggestions en pastilles (choisie : `primary` pleine ; « Le reste : … »), erreur d'envoi **dans** la feuille, notice surprise, « Participer » / « Mettre à jour ma part ».
- **Propositions** : carte par idée (vignette, titre, auteur, notes), « Accepter » + « Il l'a déjà » côte à côte (empilés dès 130 %), « Refuser » en lien avec dialogue et mot facultatif, rappel œil barré. Bandeau d'accueil : carte `surface`, ampoule `potFg` sur disque `potBg`, pluriel `family_pending_ideas` + précision, chevron.
- **Ajout rapide** (« + ») : « Ajouter une envie pour… » / « Proposer une idée pour… » en cartes de `ChildRow` (chevron, ou ampoule pour une idée), notice surprise, état vide « Aucun enfant ».
- Limites : pas de suivi des prix, ni « Dire merci », ni Père Noël / mode enfant (tickets dédiés) ; la feuille « Autre lien » ne lit pas l'aperçu du lien (prix à saisir). Conversion indicative : voir #74.

### Mes achats, budgets et devises (#74)

Écrans dans `ui/purchases` (`PurchasesScreen` : `ReservationCard`, `PotCard`) et `ui/budgets` (`BudgetsSection` : `BudgetCard`, `BudgetInvitation` ; `BudgetEditorScreen`). Textes et règles repris de `MyPurchasesView`, `BudgetsSection`, `BudgetEditorView` et `CurrencyViews`.

- **Mes achats** : grand titre, `LazyColumn` (560 dp au plus) dans un `PullToRefreshBox`. Résumé en `fcCard` : compteur `title` tabulaire + libellé │ filet `separator` │ totaux par devise joints par « + » (`headline` tabulaire), « Budget estimé », « ≈ 412 € au total · indicatif » (`captionBold` / `textSecondary`) ; un seul nœud TalkBack, total converti dit en phrase ; les deux blocs s'empilent dès 150 %. Chargement `GiftLoadingView` ; vide `mascot_sleeping` (le titre et les budgets restent). Groupes : événement `headline` + date `caption` (un titre TalkBack), « Pour Léo » `captionBold` (titre TalkBack).
- **Carte de réservation** : vignette 56 dp, titre 2 lignes, 🇨🇭 prix + `ApproxPriceText` + boutique (passe à la ligne), « ✓ Acheté » `availableFg`, avis possédé (« Bien reçu ! » `availableFg` + cadeau, sinon `FCNotice` warning). Toute la carte ouvre la fiche. Bascule ronde « acheté » (cercle `textSecondary` → disque coché `availableFg`, fondu 200 ms, haptique `ToggleOn/Off`, `Role.Checkbox`, 48 dp). En bas à droite : ↗ « Ouvrir la boutique » et ⋮ « Plus d'actions » (« Ouvrir la boutique », « Annuler la réservation » en `takenFg`), mêmes entrées en actions TalkBack. iOS : balayage « Annuler » sans confirmation ; Android confirme (« Garder » / « Annuler la réservation »).
- **Carte de cagnotte** : vignette, titre, « Pour Zoé · Noël 2026 », pastille « Ma part : 40,00 € » `potBg` / `potFg`, prix converti, « 120,00 € réunis · 3 participants » ; ⋮ « Quitter la cagnotte » + confirmation. Écart iOS : la carte ouvre la fiche (on y modifie sa part).
- **Budgets** : `SectionHeader` + « + ». `BudgetCard` : périmètre `headline`, « 180 / 200 CHF » `callout` semi-gras tabulaire, jauge capsule 8 dp (≤ ×1,5 avec la police) sur piste `separator`, remplissage au ressort (instantané si animations réduites). Niveaux : à l'aise `secondary` + « Reste 80 CHF » discret ; presque atteint **`potFg`** (jauge, icône compteur, texte semi-gras — pas l'ambre) ; dépassé `takenFg` (jauge, montant, triangle, filet 1,5 dp à 50 %). Un seul nœud TalkBack « Léo · Noël 2026, 180 CHF dépensés sur 200 CHF, Plus que 20 CHF », toucher = modifier. Invitation : pastille `pastelBlue` / `mineFg` (barres), chevron, toute la carte est le bouton. Total indicatif sous les cartes.
- **Éditeur de budget** (plein écran, ✕) : carte de périmètre en `PickerRow` (Enfant / Événement, « Tous les… » en tête), rappel ⓘ « Choisis un enfant, un événement, ou les deux. » en fondu ; carte « Montant » : champ `background` 52 dp, montant `title` tabulaire (focus à la création), bordure `primary` 2 dp au focus, `takenFg` + message si illisible, devise en pastille-menu `primary` ▾ ; `FCNotice` cadenas ; « Créer le budget » / « Enregistrer » (coche) ; « Supprimer ce budget » `takenFg` dans sa carte, confirmation « Tes réservations ne changent pas. ».
- **Mention de conversion** (`IndicativeRateNote`) en pied de Mes achats et de la liste d'un enfant dès qu'un prix converti y est affiché (iOS : fiche seulement ; ajout Android, la liste montrant aussi des « ≈ »).
- Icônes ajoutées : `BarChart`, `Gauge`, `CircleOutline` (Material, Apache 2.0). Galerie debug : section « Budgets ».
- Remerciements : voir [#75](#recherche-remerciements-et-mode-enfant-75).

### Recherche, remerciements et mode enfant (#75)

Écrans dans `ui/search` (`SearchScreen`), `ui/gifts/ThanksScreens.kt` (`ThanksComposerScreen`, `ThanksInboxScreen`) et `ui/childmode` (`ChildModeScreen`, `ChildModeEntryCard`, `ChildModeExitButton`). Textes repris de `SearchView`, `ThanksComposerView`, `ThanksInboxView`, `ChildMode*`, et des parties « Dire merci » de `GiftDetailView` / « Remerciements » de `MyPurchasesView`.

- **Recherche** : grand titre, `FCTextField` « Cadeau, enfant… » (touche Rechercher = fermer le clavier), puces en `LazyRow` d'un bord à l'autre — capsule 36 dp dans une cible 48 dp, active `primary` + coche `onPrimary`, inactive `surface` bordée `separator`, fondu 150 ms. « Disponibles » est une case à cocher TalkBack, « Tous pays » et les pays des boutons radio. Résultats par enfant sous des **en-têtes collés** (`stickyHeader`, fond `background` opaque : avatar 28 dp, prénom, compteur tabulaire ; titre TalkBack « Léa, 3 cadeaux »). `GiftCard` sans badge pour une liste de parent. Vide `mascot_thinking`, chargement `GiftLoadingView`, tirer pour actualiser.
- **Dire merci** (plein écran, ✕) : en-tête vignette 56 dp + titre + « Reçu par Léa » (un nœud), `FCNotice` surprise, champ multiligne `surface` ≥ 140 dp (grandit avec la police, bordure `primary` au focus, `takenFg` au-delà de 1000), compteur « n / 1000 » tabulaire lu « n caractères sur 1000 ». Photo : bouton pointillé `separator` 72 dp → Photo Picker ; aperçu 200 dp rayon `card`, croix 34 dp sur `surface` dans 48 dp. Erreur d'envoi en `FCNotice` warning dans le formulaire (région live) ; échec de chargement = état vide `mascot_thinking` + « Fermer ». « Merci envoyé ! » en fondu (instantané si animations réduites), haptique `Confirm`, région live.
- **Remerciements** : carte par merci (avatar 40 dp, « De Claire » / « Des parents de Léa », « Pour « titre » · il y a 2 heures » via `DateUtils`), message sélectionnable, photo 220 dp, filet, ligne « Me faire connaître » entière en `Role.Switch` — interrupteur **`secondary`** comme le `.tint` iOS (seul écart à l'interrupteur `primary` du Profil), désactivé pendant l'enregistrement. Barre : filet au défilement ; tirer pour actualiser.
- **Mes achats** : enveloppe 48 dp en face du grand titre, pastille du nombre (`primary` / `onPrimary`, cerclée `background`) ; TalkBack « Remerciements reçus, 2 ». Bannière `mascot_love` + « 2 remerciements reçus » + « Lis les messages des parents » + chevron.
- **Fiche** : « Offert par Mamie et Paul » (cœur `heart` + `headline`), « Dire merci » (enveloppe pleine), légende centrée selon qu'il y a des donateurs connus.
- **Mode enfant** : barre fixe (compteur ♥ en capsule `surface`, Nunito extra-gras, rebond ; « Quitter »), en-tête avatar 56 dp + `largeTitle` + consigne Nunito 20 sp + `mascot_love` (≤ 112 dp, masquée dès 150 %). Grille 2 colonnes (1 dès 150 %, 3 dès 600 dp, 4 dès 840 dp), cartes rayon 28 dp, photo carrée, titre Nunito 20 sp sur deux lignes réservées, cœur 52 dp (×1,5 max). Posé : contour `heart` 3 dp, rebond, gerbe de 6 cœurs (absente si animations réduites), haptique `ToggleOn/Off`. Bandeau `primary` / `onPrimary` 3,5 s (cadenas « Pour quitter… » ou alerte « Oups… »), région live, toucher pour fermer. Échec de chargement : `mascot_thinking` « Ta liste n'a pas pu s'afficher » (ajout Android : iOS ne charge pas dans cet écran).
- **Quitter** : capsule `surface` ombrée ≥ 48 dp, anneau 28 dp (piste `separator`, remplissage `primary` arrondi) + cadenas, « Quitter » → « Maintiens… » en fondu. Le temps est compté sur l'horloge d'images (`withFrameMillis`) et non par une animation : « Supprimer les animations » n'abrège pas les 3 s. Relâché ou sorti du bouton trop tôt : l'anneau retombe (250 ms) et l'astuce s'affiche ; plein : haptique `LongPress` puis vérification. TalkBack : bouton « Quitter le mode enfant » + action personnalisée du même nom → vérification directe si l'appareil en a une, sinon l'astuce.
- **Entrée** (`ChildModeEntryCard`) : pastille `pastelPink` 52 dp avec `mascot_love`, « Mode enfant », « Léo choisit ses envies en grand, sans prix ni statut. », chevron ; toute la carte est le bouton.

## Mode enfant (#36)

Écran plein écran (`fullScreenCover`) ouvert depuis la carte « Mode enfant » en tête de la liste d'un de mes enfants (`ChildModeEntryCard`, visible seulement pour ses parents, hors archive, liste non vide).

- **Contenu** : seulement les souhaits de la liste (`ChildModeItem` : photo, nom, cœur). Le type ne porte ni prix, ni statut, ni réservation, ni cagnotte, ni notes : rien de tout cela ne peut s'afficher par erreur. Idées et « possède déjà » exclus.
- **Mise en page** : en-tête « Les envies de Léo » (`largeTitle`) + avatar + `mascot_love` ; grille 2 colonnes (1 aux tailles d'accessibilité) de grandes cartes photo carrées, rayon 28 (plus rond que `Radius.card` : registre jouet), titre en SF Pro Rounded `.title3` bold sur deux lignes réservées.
- **Cœur** : toute la carte est la cible. Posé : disque blanc avec cœur plein `heart`, contour `heart` de 3 pt autour de la carte, rebond du symbole, petite gerbe de cœurs (absente si Réduire les animations), retour haptique. Enregistré tout de suite via `setPriority` ; en cas d'échec, le cœur revient en arrière et un bandeau l'explique.
- **Sortie protégée** : bouton « Quitter » à maintenir 3 s (anneau `primary` qui se remplit), puis Face ID / Touch ID / code (`LocalAuthentication`). Un simple tap n'ouvre qu'un bandeau « Pour quitter, un adulte garde le doigt sur « Quitter » pendant 3 secondes. ». Sans code configuré sur l'appareil, l'appui long suffit. VoiceOver : l'activation passe directement à Face ID / code.
- Barre d'état et indicateur d'accueil masqués. Pour verrouiller l'app elle-même, l'Accès guidé d'iOS reste l'outil adapté.

## Budgets (#40)

Section « Budgets » de Mes achats (`BudgetsSection`), sous le récapitulatif. Privé : seul l'auteur voit ses budgets.

- **Carte** (`BudgetCard`) : périmètre (« Léo · Noël 2026 »), « 180 / 200 CHF » en chiffres tabulaires, jauge capsule de 8 pt (mise à l'échelle Dynamic Type) sur rail `separator`.
- **Niveaux** (`BudgetMath.level`) : confortable → jauge `secondary` + « Reste 80 CHF » ; à partir de 85 % → jauge `accentAmber` + « Plus que 20 CHF » (icône jauge) ; dépassé → jauge et montant `takenFg`, « Dépassé de 45 € » avec triangle, filet `takenFg` autour de la carte. Le niveau est toujours dit en texte et en icône, jamais par la couleur seule.
- **Devises** : budget dans une autre devise que le profil → « ≈ 227 / 186 CHF » (`ApproxPriceText`). Total « ≈ X sur Y au total · indicatif » seulement si les budgets mélangent les devises **et** ne se recoupent pas (`BudgetMath.areDisjoint`) : un budget « Léo » et un budget « Noël » comptent les mêmes cadeaux, les additionner tromperait.
- **Éditeur** (`BudgetEditorView`, feuille) : enfant (« Tous les enfants »), événement (« Tous les événements »), au moins l'un des deux ; montant en `title` + devise en menu ; rappel « seuls les cadeaux dont un lien est dans cette devise sont comptés » ; `FCNotice` de confidentialité ; suppression en texte `takenFg` avec confirmation.

## Suivi des prix (#39)

- **Fiche cadeau** : section « Suivi du prix » (`PriceHistorySection`) uniquement sur un cadeau que j'ai réservé, et seulement s'il existe des relevés (le serveur ne renvoie rien aux autres, parents compris). Une ligne par boutique (`PriceTrendRow`) : dernier prix, courbe en escalier de 48 pt (Swift Charts, sans axes, masquée pour VoiceOver), tendance en texte (« CHF 30,00 de moins depuis le 27 sept. » en `availableFg` avec flèche ; hausse et stabilité en `textSecondary`), pastille « Plus en stock » (`takenBg` / `takenFg`). Ruptures et baisses en tête.
- **Notification** : toucher une alerte « Prix en baisse » / « Plus en stock » (`thread-id` `price-<item_id>`) ouvre la fiche du cadeau en feuille au-dessus de l'onglet courant (`NotificationRouter`, `PriceAlertPresenter`). Cadeau introuvable → état vide `mascot_thinking`.

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
