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

## Conventions

- Branches : `<type>/<N>-<slug>` (`feat`, `fix`, `chore`, `docs`, `design`), toujours depuis `main`.
- Commits : Conventional Commits avec référence du ticket, ex. `feat(auth): ajout du login (#12)`.
- Jamais de commit direct sur `main`.
- Toute PR contient `Closes #N`.
- Les changements de statut passent **uniquement** par `scripts/ticket-status.sh <N> "<Statut>"`.
- Le design UI passe par l'agent `ui-designer`, qui utilise les skills `impeccable` et `ui-ux-pro-max`.

## Configuration

`.claude/workflow.env` : propriétaire et numéro du board GitHub Projects, repo, nom du champ de statut, branche de base.
