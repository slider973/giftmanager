# giftmanager

Équipe d'agents Claude Code qui gère le cycle de vie d'un ticket GitHub : de l'issue jusqu'à la Pull Request, en déplaçant automatiquement le ticket sur le board GitHub Projects.

```
Issue #N ──► ticket-manager ──► tech-planner ──► developer ─┐
   │         (In Progress)                       ui-designer ┤
   │                                                         ▼
   └──────────────── Done ◄── pr-manager ◄── code-reviewer (APPROVE)
```

## Agents (`.claude/agents/`)

| Agent | Rôle | Modèle |
|---|---|---|
| `ticket-manager` | Lit l'issue, la passe en **In Progress**, l'assigne, crée la branche | haiku |
| `tech-planner` | Analyse le code et découpe le ticket en tâches logique / UI | sonnet |
| `developer` | Implémente la logique et les tests | sonnet |
| `ui-designer` | Implémente l'UI avec les skills **impeccable** et **ui-ux-pro-max** | opus |
| `code-reviewer` | Relit le diff, vérifie les critères d'acceptation, verdict | sonnet |
| `pr-manager` | Pousse, crée la PR (`Closes #N`), passe le ticket en **Done** | haiku |

L'orchestration se fait via le skill `/ship-ticket <N>` (`.claude/skills/ship-ticket/`).

## Installation

1. **Scope GitHub Projects** pour la CLI `gh` :
   ```bash
   gh auth refresh -s project
   ```
2. **Board** : créer un GitHub Project (vue Board) avec un champ `Status` contenant au minimum `Todo`, `In Progress`, `Done`, puis renseigner son numéro dans `.claude/workflow.env` (`PROJECT_NUMBER`).
3. **Skills de design** (utilisées par `ui-designer`) :
   ```
   /plugin marketplace add pbakaus/impeccable
   /plugin install impeccable@impeccable
   /plugin marketplace add nextlevelbuilder/ui-ux-pro-max-skill
   /plugin install ui-ux-pro-max@ui-ux-pro-max-skill
   ```
4. **(Optionnel) GitHub Action** `project-sync.yml` : ajouter le secret `PROJECT_TOKEN` (PAT classique, scopes `repo` + `project`) pour que les PR ouvertes à la main passent aussi le ticket en Done.

## Utilisation

```bash
claude
> /ship-ticket 42
```

Déplacer un ticket à la main :

```bash
scripts/ticket-status.sh 42 "In Progress"
scripts/ticket-status.sh 42 "Done"
```

## Réutiliser dans un autre projet

Copier `.claude/`, `scripts/` et `.github/workflows/project-sync.yml`, puis adapter `.claude/workflow.env`.

## Développement de l'app (Famille Cadeaux)

Prérequis : Xcode, [XcodeGen](https://github.com/yonaskolb/XcodeGen), Supabase CLI, Docker, 1Password CLI (`op`), `uv`.

```bash
scripts/secrets/inject.sh                 # config iOS depuis 1Password (Supabase prod)
supabase start                            # Supabase local (ports 554xx), migrations + seed
cd ios && xcodegen generate && open GiftManager.xcodeproj
```

Pour viser le Supabase local en Debug, créer `ios/Config/Local.xcconfig` (ignoré par git) :

```
SUPABASE_URL = http:/$()/127.0.0.1:55421
SUPABASE_PUBLISHABLE_KEY = <clé publishable affichée par `supabase status`>
```

Tests : `xcodebuild test` (scheme GiftManager) et `supabase test db` (pgTAP).

## Publication TestFlight

Apple exige le SDK iOS 26 : le build tourne sur GitHub Actions (`ios-release.yml`, macOS 26 / Xcode 26).

```bash
scripts/apple/release.sh                  # certificat/profil à jour, secrets synchronisés, build + envoi TestFlight
```

`scripts/apple/setup-distribution.sh` crée si besoin le certificat Apple Distribution (stocké dans 1Password, item `apple-distribution`) et le profil App Store « Famille Cadeaux App Store ».
