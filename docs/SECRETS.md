# Secrets et identifiants

Tous les secrets du projet sont stockés dans **1Password**, coffre **`giftmanager`**, et lus avec la CLI `op`. Aucune valeur n'est écrite en clair dans le dépôt.

## Items du coffre

| Item | Champs | Où c'est utilisé |
|---|---|---|
| `supabase` | `project_ref`, `url`, `publishable_key`, `secret_key`, `access_token` | App iOS (url + publishable_key uniquement), CLI Supabase, CI |
| `supabase-db` | `password` (généré), `host`, `port`, `username` (pooler session) | Création du projet Supabase, `supabase db push` |
| `apple-developer` | `team_id`, `bundle_id` | Signature Xcode, provider Apple dans Supabase |
| `app-store-connect` | `key_id`, `issuer_id`, `private_key` (contenu du `.p8`) | Upload TestFlight (CI / fastlane) |
| `github` | `project_token` | GitHub Action `project-sync` |
| `apple-distribution` | `certificate_id`, `p12_password`, fichier `p12` | Signature des builds TestFlight (CI) |
| `apns` | `key_id`, `private_key` (contenu du `.p8` APNs) | Notifications push (fonction Edge `notify-new-items`), via `scripts/secrets/sync-supabase.sh` |
| `cron` | `password` (généré) | Tâches planifiées (rappels d'anniversaires, suivi des prix) : secret `CRON_SECRET` des fonctions Edge + secret Vault `cron_secret` lu par pg_cron |

Les valeurs pas encore connues valent `A_RENSEIGNER`.

## Fichiers

| Fichier | Suivi par git | Rôle |
|---|---|---|
| `.env.op` | oui | Variables d'environnement → références `op://` |
| `ios/Config/Secrets.xcconfig.tpl` | oui | Modèle de config iOS avec références `op://` |
| `ios/Config/Secrets.xcconfig` | **non** | Généré par `scripts/secrets/inject.sh` |
| `*.p8`, `.env`, `.env.*` | **non** | Ne jamais commiter |

## Commandes

```bash
op signin                                   # une fois par session
scripts/secrets/setup-1password.sh          # crée le coffre et les items manquants (idempotent)
scripts/secrets/inject.sh                   # génère ios/Config/Secrets.xcconfig
scripts/secrets/sync-github.sh              # pousse les secrets CI vers GitHub Actions
scripts/secrets/sync-supabase.sh            # pousse la clé APNs vers les secrets de la fonction Edge
op run --env-file=.env.op -- <commande>     # lance une commande avec les secrets en variables d'env
```

Exemples :

```bash
op run --env-file=.env.op -- sh -c 'supabase link --project-ref "$SUPABASE_PROJECT_REF" -p "$SUPABASE_DB_PASSWORD"'
op run --env-file=.env.op -- supabase db push
```

## Renseigner une valeur

```bash
op item edit supabase --vault giftmanager "project_ref[text]=abcd1234" "publishable_key[text]=sb_publishable_..."
op item edit app-store-connect --vault giftmanager "private_key[concealed]=$(cat ~/Downloads/AuthKey_XXXX.p8)"
rm ~/Downloads/AuthKey_XXXX.p8                # le fichier ne doit pas traîner sur le disque
```

Puis relancer `scripts/secrets/inject.sh` et `scripts/secrets/sync-github.sh`.

## Tâches planifiées (pg_cron)

Les migrations planifient `birthday-reminders` (08:00 UTC) et `track-prices` (05:00 UTC). pg_cron appelle les fonctions Edge avec l'en-tête `x-cron-secret`. Deux secrets Vault sont à créer **une fois** sur la base de prod (sans eux l'appel est un no-op) :

```sql
-- éditeur SQL Supabase ; remplacer les deux valeurs (cron_secret : `op read op://giftmanager/cron/password`)
select vault.create_secret('https://<project_ref>.supabase.co/functions/v1', 'edge_functions_url');
select vault.create_secret('<valeur de op://giftmanager/cron/password>', 'cron_secret');
```

et le même secret côté fonctions : `scripts/secrets/sync-supabase.sh` pousse `CRON_SECRET` (item `cron`).

## Règles

- La `secret_key` (service_role) Supabase ne va **jamais** dans l'app iOS : uniquement en CI et dans les Edge Functions.
- Les secrets passent par stdin ou par `op run`, jamais en argument de commande ni dans les logs.
- Supabase local (`supabase start`) utilise les clés de démo publiques de la CLI : rien à stocker.
- La CI exécute gitleaks sur chaque PR.
