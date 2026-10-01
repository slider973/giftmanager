# Famille Cadeaux — Design

Référence visuelle : [`docs/design/maquette-v1.png`](docs/design/maquette-v1.png)

Nom affiché de l'app : **Famille Cadeaux**. Promesse : *« Des idées. Moins de doublons. Plus de magie. »*

## Direction

Chaleureux, doux et festif sans être enfantin. Fond crème, cartes blanches très arrondies, un bleu nuit profond pour les actions principales, des pastels pour les catégories, des illustrations de mascottes (loutres) pour l'onboarding et les états vides. Lisible pour les grands-parents.

## Tokens (relevés sur la maquette — à affiner)

### Couleurs

| Token | Valeur | Usage |
|---|---|---|
| `background` | `#FBF4EC` | Fond d'écran (crème) |
| `surface` | `#FFFFFF` | Cartes, champs, feuilles |
| `primary` | `#1E2A47` | Boutons principaux (« Continuer », « Ajouter à la liste »), FAB +, titres |
| `secondary` | `#2F7D6D` | CTA secondaire vert (« Ajouter à la liste » sur la fiche cadeau) |
| `textPrimary` | `#1B1F2A` | Titres, noms de cadeaux |
| `textSecondary` | `#6B6F7B` | Sous-titres, métadonnées (prix, boutique, dates) |
| `accentHeart` | `#E5484D` | Cœur « très envie » |
| `statusAvailableBg` / `Fg` | `#DDF3E6` / `#2E8B57` | Badge « Disponible » |
| `statusTakenBg` / `Fg` | `#FDE3E3` / `#D64545` | Badge « Déjà pris » |
| `pastelPink` | `#F9D9DC` | Pastille « Groupes » |
| `pastelMint` | `#D5F0E4` | Pastille « Listes » |
| `pastelBlue` | `#DCE8FB` | Pastille « Liens / boutiques » |
| `pastelPeach` | `#FCE3D2` | Pastille « Surprise » |
| `pastelLavender` | `#E6E1FA` | Pastille « Protection » |

Prévoir une variante sombre (fond bleu nuit, cartes `#262F48`) en conservant les badges lisibles.

### Typographie

Police système **SF Pro** (Dynamic Type obligatoire) :

| Style | Taille / graisse | Exemple |
|---|---|---|
| `largeTitle` | 34 / Bold | « Notre famille », « Famille Cadeaux » |
| `title` | 22 / Bold | « Bienvenue sur Famille Cadeaux », nom de l'enfant |
| `headline` | 17 / Semibold | Nom d'un cadeau, nom d'un événement |
| `body` | 15 / Regular | Textes courants |
| `caption` | 12 / Regular | Prix, boutique, « 8 ans · Ses envies » |

### Formes et espacements

- Rayons : cartes 20, vignettes produit 14, boutons pilule (capsule), badges capsule.
- Ombres très légères (`y: 2, blur: 8, opacity: 0.06`).
- Grille d'espacement 4 pt ; marges d'écran 20 ; espacement entre cartes 12.
- Cibles tactiles ≥ 44 pt.

## Écrans de la maquette

| # | Écran | Éléments clés | Ticket |
|---|---|---|---|
| 1 | **Onboarding** | Illustration loutres + cadeau, titre, sous-titre, pagination à points (5 pages), bouton « Continuer » | #5 |
| 2 | **Notre famille** | Avatars empilés (+N), « 12 membres · 3 foyers », onglets Événements / Membres / Paramètres, événements à venir (icône, date, âge, avatars), événements passés, bouton + | #6, #8 |
| 3 | **Liste d'un enfant** | Avatar + prénom (sélecteur ▾), « 8 ans · Ses envies », onglets Liste (n) / Possède déjà (n) / Idées (n), cartes cadeau : vignette, nom, drapeau + prix, boutique, cœur, badge de statut | #9, #10, #11 |
| 4 | **Ajouter un cadeau** | Champ URL, aperçu (image, titre, boutique + drapeau, prix), « Ajouter à la liste », « Modifier les informations », « Autres liens pour ce cadeau » (boutique, drapeau, prix, copier) | #9 |
| 5 | **Détail du cadeau** | Grande image, cœur, titre, boutique, « Liens par pays » (drapeau, boutique, prix, lien externe), CTA vert | #11 |

**Barre d'onglets** : Accueil · Recherche · **+** (central) · Notifications · Profil.

## Composants à créer

`GiftCard`, `StatusBadge` (disponible / déjà pris / masqué), `PriorityHeart`, `StoreLinkRow` (drapeau + boutique + prix + action), `CountryFlag`, `ChildHeader`, `EventRow`, `AvatarStack`, `PrimaryButton`, `SegmentedTabs`, `FeaturePill`, `EmptyState` (avec mascotte).

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
