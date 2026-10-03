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
- **Foyer** : un ou deux parents et leurs enfants, dans un pays donné. Il appartient à ses parents, **pas** à un groupe : il est *partagé* vers une ou plusieurs familles. Rejoindre une nouvelle famille (belle-famille, famille recomposée) n'oblige donc jamais à recréer ses enfants.
- **Enfant** : profil sans compte, géré par les parents de son foyer.
- **Événement** : Noël, global et commun à toutes les familles, ou anniversaire, porté par un enfant et visible dans chacune de ses familles.
- **Liste de souhaits** : les cadeaux d'un enfant pour un événement.
- **Cadeau** : titre, image, notes, priorité, et un ou plusieurs **liens d'achat** (URL, boutique, pays, prix, devise).
- **Idée** : suggestion de cadeau proposée par un adulte pour un enfant d'un autre foyer. Par défaut **soumise à ses parents**, qui l'acceptent, la refusent ou signalent que l'enfant l'a déjà : eux seuls savent si le cadeau convient (âge, doublon, règles de la maison), et le secret porte sur la réservation, pas sur l'objet. Une acceptation la fait entrer dans la liste de souhaits, son auteur restant crédité. L'auteur peut choisir de **ne pas la soumettre** : elle reste alors invisible des parents, au prix d'aucune vérification.
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
- Une réservation faite dans une famille rend le cadeau « déjà pris » dans **toutes** les familles où l'enfant est visible. Sans cela l'anti-doublon inter-familles ne fonctionnerait pas. Aucune information nominative ne traverse la frontière entre groupes : le statut reste `pris` / `disponible`.
- Le statut public d'un cadeau (`disponible` / `pris`) est fourni par une fonction serveur qui :
  - renvoie `pris` ou `disponible` aux membres du groupe **qui ne sont pas** parents de l'enfant ;
  - ne renvoie **aucun statut** aux parents de l'enfant (mode surprise).
- **Parent qui veut acheter sur la liste de son propre enfant** : il peut réserver ; si le cadeau est déjà pris, le serveur répond seulement « ce cadeau n'est plus disponible » pour **cet** article, sans jamais dire par qui. Aucune vue globale ne lui est montrée.
- Les notifications ne révèlent jamais l'identité de l'acheteur.
- À la réservation, les membres qui ne sont ni parents de l'enfant ni l'acheteur reçoivent une alerte **anonyme** (« un cadeau vient d'être réservé »), sans nom ni titre de cadeau, pour éviter un second achat. Elle n'est envoyée qu'à partir de **deux** destinataires — à un seul, « quelqu'un a réservé » le désignerait par élimination — et au plus une fois par enfant toutes les 30 minutes. Rien n'est notifié sur « acheté » ni sur une annulation.
- **Risque accepté** : un parent déterminé pourrait tenter de réserver un à un les cadeaux de son enfant pour deviner lesquels sont pris. Cela demande une action volontaire, laisse des réservations à son nom et ne révèle jamais l'acheteur.
- Un parent ne peut ni quitter son foyer ni en rejoindre un autre via l'API (sinon il lèverait le mode surprise) ; un second parent rejoint le foyer avec le **code du foyer**.
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
households(id, name, country, invite_code)            -- n'appartient plus à un groupe
household_groups(household_id, group_id)              -- partage du foyer vers les familles
household_members(household_id, user_id)              -- parents ; un seul foyer par utilisateur
children(id, household_id, first_name, birthdate, avatar)
events(id, group_id NULL, kind: christmas|birthday|other, title, date, child_id NULL)
wish_items(id, child_id, event_id, kind: wish|idea, title, notes, image_url, priority, owned, created_by)
item_links(id, item_id, url, store, country, price, currency)
reservations(id, item_id UNIQUE, user_id, status: reserved|purchased, created_at)
```

Portée des événements : Noël a `group_id` et `child_id` nuls (global) ; un anniversaire a `group_id` nul et suit son enfant ; les autres événements appartiennent à un groupe.

Fonctions serveur : `item_public_status(item_id)`, `reserve_item(item_id)`, `join_group(invite_code)`, `share_household_with_group(group)`, `unshare_household_from_group(group)`.

## Hors périmètre v1

Conversion de devises, extension de partage iOS, Android ou web, listes de souhaits d'adultes, cagnottes communes, suggestions de cadeaux par IA, notifications push (v1.1).

## Jalon

**Noël 2026** — version TestFlight distribuée à la famille au plus tard le **1er décembre 2026**.
