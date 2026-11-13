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
scripts/release.sh 1.1.0             # pose le tag v1.1.0 → builds TestFlight (iOS) et Google Play test interne (Android)

# 3. une fois la version validée à l'usage par la famille
scripts/promote.sh 1.1.0             # avance production sur ce tag
```

`release.sh` refuse de publier si : on n'est pas sur `main`, des modifications locales
traînent, `main` diverge de `origin`, le tag existe déjà, ou la version est inférieure à la
précédente. Les notes de version sont les commits depuis le tag précédent.

`promote.sh` refuse une promotion qui ferait **reculer** `production` : elle ne peut avancer
que vers un descendant. En cas de problème sur une version suivante, `production` reste le
dernier état connu comme fonctionnel.

Le tag `v1.1.0` fixe la version marketing de l'app iOS (`MARKETING_VERSION`) et le `versionName`
Android : le dépôt, App Store Connect et Google Play ne peuvent plus diverger. Le numéro de
build iOS reste un horodatage, toujours croissant comme l'exige Apple ; le `versionCode`
Android est le nombre de minutes depuis le 2026-01-01 (`scripts/android/version-code.sh`),
croissant aussi et sous la limite de Google Play. Les deux workflows (`ios-release.yml`,
`android-release.yml`) partent du même tag et sont indépendants.

Numérotation : `PATCH` pour un correctif, `MINOR` pour une fonctionnalité, `MAJOR` pour une
rupture. Un push sur `main` ne déclenche **aucun** build — seul un tag le fait.

Rejouer un build sans créer de version : `gh workflow run ios-release.yml` (iOS) ou
`gh workflow run android-release.yml -f publish=false` (Android : construit l'AAB sans l'envoyer ;
`-f publish=true` l'envoie sur la piste de test interne). Suivi : `gh run list --workflow=android-release.yml --limit 1`.

APK Android à installer directement, sans Google Play (testeurs comme Marina) : `android-apk.yml`
construit un APK release signé à chaque PR ou push sur `main` qui touche `android/`, gardé 14 jours en
artefact (`gh run download -n giftmanager-<version>.apk`). Ce n'est pas une version : rien n'est publié.

## Conventions

- Branches : `<type>/<N>-<slug>` (`feat`, `fix`, `chore`, `docs`, `design`), toujours depuis `main`.
- Commits : Conventional Commits avec référence du ticket, ex. `feat(auth): ajout du login (#12)`.
- Jamais de commit direct sur `main`.
- Toute PR contient `Closes #N`.
- Les changements de statut passent **uniquement** par `scripts/ticket-status.sh <N> "<Statut>"`.
- Le design UI passe par l'agent `ui-designer`, qui utilise les skills `impeccable` et `ui-ux-pro-max`.

## Mémoire partagée (people-knowledge)

La mémoire de Jonathan vit dans le dépôt privé `slider973/people-knowledge` ; Gift Manager y a son
tableau de bord `projets/giftmanager/giftmanager.md`. **Elle se met à jour au moment du travail, pas
en fin de session** — dans le même mouvement que :

- une PR ouverte, fusionnée ou fermée ;
- une étiquette de version (`release.sh`) ou une promotion (`promote.sh`) ;
- une décision prise, ou une affirmation de la fiche qui se révèle fausse.

Procédure : lire puis suivre `skills/people-knowledge/SKILL.md` de ce dépôt (versions et numéros de PR
exacts, ligne datée dans l'historique du tableau de bord, entrée dans `log.md`, `check.py` → `OK`,
commit puis `git pull --rebase && git push` sur sa branche `main`). Tableau de bord < 60 lignes.

Si le dépôt n'est pas dans la session, l'ajouter (`add_repo` dans le cloud, sinon `git clone`) ; si
c'est impossible, le dire à Jonathan à la fin de la tâche, avec ce qu'il faudrait écrire.

## Secrets

- Tous les identifiants sont dans 1Password, coffre `giftmanager` (voir `docs/SECRETS.md`).
- Ne jamais écrire une valeur secrète dans un fichier suivi, un commit, une PR, un commentaire d'issue ou une sortie de commande.
- Nouveau secret : l'ajouter dans 1Password, puis référencer `op://giftmanager/<item>/<champ>` dans `.env.op` ou `ios/Config/Secrets.xcconfig.tpl`.
- Commandes qui ont besoin de secrets : `op run --env-file=.env.op -- <commande>`.
- La `secret_key` Supabase n'entre jamais dans l'app iOS.

## Configuration

`.claude/workflow.env` : propriétaire et numéro du board GitHub Projects, repo, nom du champ de statut, branche de base.
