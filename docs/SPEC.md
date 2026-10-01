# giftmanager — Spécifications v1 (objectif : Noël 2026)

## Vision

Une app iOS pour coordonner les cadeaux des enfants au sein d'une famille élargie dispersée dans plusieurs pays (ex. Suisse et France). Chaque parent crée les listes de souhaits de ses enfants ; les autres membres de la famille choisissent un cadeau, le réservent **anonymement**, et personne n'achète deux fois le même cadeau ni un jouet que l'enfant possède déjà.

## Décisions produit

| Sujet | Décision |
|---|---|
| Plateforme | App iOS native (SwiftUI, iOS 17+) |
| Backend | Supabase (Postgres + Row Level Security + Edge Functions) |
| Authentification | Sign in with Apple |
| Enfants | Profils gérés par les parents, **sans compte**. L'enfant choisit ses cadeaux avec son parent, sur le téléphone du parent. |
| Ajout de cadeaux | Lien libre vers n'importe quelle boutique (Amazon, Galaxus, Fnac, Manor…), avec aperçu automatique ; ajout manuel possible. Plusieurs liens par cadeau, un par pays ou boutique. |
| Anonymat | **Surprise totale** : les autres membres voient « déjà pris » sans savoir par qui. Les parents de l'enfant ne voient aucune réservation sur les listes de leurs propres enfants. |
| Priorité | Noël d'abord ; anniversaires dans un second temps (même modèle d'événement). |

## Concepts

- **Groupe familial** : la famille élargie (ex. « Famille Lemaine »). On le rejoint par invitation (lien ou code).
- **Foyer** : un ou deux parents et leurs enfants, dans un pays donné. Un groupe contient plusieurs foyers.
- **Enfant** : profil sans compte, géré par les parents de son foyer.
- **Événement** : Noël (pour tous les enfants du groupe) ou anniversaire (pour un enfant).
- **Liste de souhaits** : les cadeaux d'un enfant pour un événement.
- **Cadeau** : titre, image, notes, priorité, et un ou plusieurs **liens d'achat** (URL, boutique, pays, prix, devise).
- **Idée** : suggestion de cadeau proposée par un adulte pour un enfant d'un autre foyer. Visible des autres membres, **jamais des parents de l'enfant** ; un membre peut la réserver comme un cadeau de la liste.
- **Possède déjà** : cadeau marqué comme déjà possédé par l'enfant, visible de tous, pour éviter les doublons.
- **Réservation** : un membre s'engage à offrir un cadeau (`réservé` → `acheté`). Seul l'auteur de la réservation la voit.

## Parcours principaux

1. **Créer le groupe** — Je me connecte avec Apple, je renseigne mon pays (CH), je crée « Famille Lemaine » et mon foyer, j'ajoute mes enfants.
2. **Inviter** — J'envoie un lien d'invitation par WhatsApp ou iMessage. Ma sœur en France rejoint le groupe, crée son foyer (FR) et ajoute ses enfants.
3. **Faire la liste avec l'enfant** — Sur mon téléphone, avec mon fils, j'ouvre sa liste de Noël, je colle des liens Galaxus. L'app affiche le titre, l'image et le prix. Je marque aussi les jouets qu'il a déjà.
4. **Offrir** — Ma mère ouvre la liste de mon fils. Elle voit en premier les liens adaptés à son pays (FR), réserve un Lego et le marque « acheté » une fois commandé. Les autres voient « déjà pris » ; moi, son parent, je ne vois rien.
5. **Mes achats** — Chaque membre a un écran récapitulatif de ses réservations : par enfant, statut, total par devise.

## Règles d'anonymat (garanties côté serveur)

- Une réservation n'est lisible **que par son auteur**.
- Le statut public d'un cadeau (`disponible` / `pris`) est fourni par une fonction serveur qui :
  - renvoie `pris` ou `disponible` aux membres du groupe **qui ne sont pas** parents de l'enfant ;
  - ne renvoie **aucun statut** aux parents de l'enfant (mode surprise).
- **Parent qui veut acheter sur la liste de son propre enfant** : il peut réserver ; si le cadeau est déjà pris, le serveur répond seulement « ce cadeau n'est plus disponible » pour **cet** article, sans jamais dire par qui. Aucune vue globale ne lui est montrée.
- Les notifications ne révèlent jamais l'identité de l'acheteur.
- Les tests automatiques des règles d'accès de la base (RLS) couvrent chacun de ces cas.

## Multi-pays

- Chaque profil a un pays et une devise (CHF, EUR…).
- Chaque lien d'achat a une boutique (détectée à partir du domaine), un pays et un prix dans une devise.
- Sur une fiche cadeau, les liens du pays du lecteur sont affichés en premier ; les autres restent accessibles.
- Les prix sont affichés dans leur devise d'origine (pas de conversion en v1).

## Aperçu des liens

- Côté app : framework `LinkPresentation` (`LPMetadataProvider`) pour le titre et l'image.
- Le prix n'est pas fiable à récupérer automatiquement (Amazon bloque souvent) : champ pré-rempli si disponible, sinon saisi à la main.
- Si l'aperçu échoue, saisie manuelle du titre et de la photo (appareil photo ou galerie).
- Extension de partage iOS (« Partager → giftmanager ») : v2.

## Modèle de données (Supabase)

```
profiles(id → auth.users, display_name, country, currency)
groups(id, name, created_by, invite_code)
households(id, group_id, name, country)
household_members(household_id, user_id)            -- parents
children(id, household_id, first_name, birthdate, avatar)
events(id, group_id, kind: christmas|birthday, title, date, child_id NULL)
wish_items(id, child_id, event_id, kind: wish|idea, title, notes, image_url, priority, owned bool, created_by)
item_links(id, item_id, url, store, country, price, currency)
reservations(id, item_id UNIQUE, user_id, status: reserved|purchased, created_at)
```

Fonctions serveur : `item_public_status(item_id)`, `reserve_item(item_id)`, `join_group(invite_code)`.

## Hors périmètre v1

Conversion de devises, extension de partage iOS, Android ou web, listes de souhaits d'adultes, cagnottes communes, suggestions de cadeaux par IA, notifications push (v1.1).

## Jalon

**Noël 2026** — version TestFlight distribuée à la famille au plus tard le **1er décembre 2026**.
