# giftmanager — workflow de dev piloté par agents

## Workflow d'un ticket

Lancer `/ship-ticket <numéro-issue>`. L'orchestrateur enchaîne :

| Étape | Agent | Effet sur le board |
|---|---|---|
| 1. Démarrage | `ticket-manager` | Ticket → **In Progress**, assigné, branche créée |
| 2. Plan | `tech-planner` | — |
| 3. Code | `developer` (logique) / `ui-designer` (UI) | — |
| 4. Review | `code-reviewer` | — (boucle jusqu'à APPROVE, 3 cycles max) |
| 5. PR | `pr-manager` | PR créée avec `Closes #N` → ticket **Done** |

## Publier une version (GitLab Flow)

Trois lignes, chacune avec son rôle :

| Branche | Rôle |
|---|---|
| `<type>/<N>-<slug>` | une fonctionnalité ou un correctif, rattaché à un ticket |
| `main` | ligne de développement, toujours déployable — **protégée** |
| `production` | ce qui tourne réellement chez les utilisateurs, point de retour |

`main` est protégée sur GitHub : commits directs refusés (y compris pour un administrateur),
force-push et suppression interdits, et la CI doit passer avant toute fusion. Le seul chemin
vers `main` est la pull request — c'est la règle « jamais de commit direct sur `main` »,
appliquée par le serveur et non par la discipline.

```bash
# 1. développer sur une branche de ticket, puis ouvrir une PR vers main
# 2. publier une version depuis main
scripts/release.sh 1.1.0 --dry-run   # ce qui serait publié, sans rien modifier
scripts/release.sh 1.1.0             # pose le tag v1.1.0 → build TestFlight

# 3. une fois la version validée à l'usage par la famille
scripts/promote.sh 1.1.0             # avance production sur ce tag
```

`release.sh` refuse de publier si : on n'est pas sur `main`, des modifications locales
traînent, `main` diverge de `origin`, le tag existe déjà, ou la version est inférieure à la
précédente. Les notes de version sont les commits depuis le tag précédent.

`promote.sh` refuse une promotion qui ferait **reculer** `production` : elle ne peut avancer
que vers un descendant. En cas de problème sur une version suivante, `production` reste le
dernier état connu comme fonctionnel.

Le tag `v1.1.0` fixe la version marketing de l'app (`MARKETING_VERSION`) : le dépôt et App
Store Connect ne peuvent plus diverger. Le numéro de build reste un horodatage, toujours
croissant comme l'exige Apple.

Numérotation : `PATCH` pour un correctif, `MINOR` pour une fonctionnalité, `MAJOR` pour une
rupture. Un push sur `main` ne déclenche **aucun** build — seul un tag le fait.

Rejouer un build sans créer de version : `gh workflow run ios-release.yml`.

## Conventions

- Branches : `<type>/<N>-<slug>` (`feat`, `fix`, `chore`, `docs`, `design`), toujours depuis `main`.
- Commits : Conventional Commits avec référence du ticket, ex. `feat(auth): ajout du login (#12)`.
- Jamais de commit direct sur `main`.
- Toute PR contient `Closes #N`.
- Les changements de statut passent **uniquement** par `scripts/ticket-status.sh <N> "<Statut>"`.
- Le design UI passe par l'agent `ui-designer`, qui utilise les skills `impeccable` et `ui-ux-pro-max`.

## Secrets

- Tous les identifiants sont dans 1Password, coffre `giftmanager` (voir `docs/SECRETS.md`).
- Ne jamais écrire une valeur secrète dans un fichier suivi, un commit, une PR, un commentaire d'issue ou une sortie de commande.
- Nouveau secret : l'ajouter dans 1Password, puis référencer `op://giftmanager/<item>/<champ>` dans `.env.op` ou `ios/Config/Secrets.xcconfig.tpl`.
- Commandes qui ont besoin de secrets : `op run --env-file=.env.op -- <commande>`.
- La `secret_key` Supabase n'entre jamais dans l'app iOS.

## Configuration

`.claude/workflow.env` : propriétaire et numéro du board GitHub Projects, repo, nom du champ de statut, branche de base.
