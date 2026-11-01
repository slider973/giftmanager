# Secrets et identifiants

Tous les secrets du projet sont stockés dans **1Password**, coffre **`giftmanager`**, et lus avec la CLI `op`. Aucune valeur n'est écrite en clair dans le dépôt.

## Items du coffre

| Item | Champs | Où c'est utilisé |
|---|---|---|
| `supabase` | `project_ref`, `url`, `publishable_key`, `secret_key`, `access_token` | Apps iOS et Android (url + publishable_key uniquement), CLI Supabase, CI |
| `supabase-db` | `password` (généré), `host`, `port`, `username` (pooler session) | Création du projet Supabase, `supabase db push` |
| `apple-developer` | `team_id`, `bundle_id` | Signature Xcode, provider Apple dans Supabase |
| `app-store-connect` | `key_id`, `issuer_id`, `private_key` (contenu du `.p8`) | Upload TestFlight (CI / fastlane) |
| `github` | `project_token` | GitHub Action `project-sync` |
| `apple-distribution` | `certificate_id`, `p12_password`, fichier `p12` | Signature des builds TestFlight (CI) |
| `apns` | `key_id`, `private_key` (contenu du `.p8` APNs) | Notifications push (fonction Edge `notify-new-items`), via `scripts/secrets/sync-supabase.sh` |
| `google-oauth` | `web_client_id`, `web_client_secret`, `android_client_id_debug`, `android_client_id_release`, `sha1_debug`, `sha1_release` | Connexion Google de l'app Android (`web_client_id`), provider Google dans Supabase (`web_client_id` + `web_client_secret`) |
| `cron` | `password` (généré) | Tâches planifiées (rappels d'anniversaires, suivi des prix) : secret `CRON_SECRET` des fonctions Edge + secret Vault `cron_secret` lu par pg_cron |

Les valeurs pas encore connues valent `A_RENSEIGNER`.

## Fichiers

| Fichier | Suivi par git | Rôle |
|---|---|---|
| `.env.op` | oui | Variables d'environnement → références `op://` |
| `ios/Config/Secrets.xcconfig.tpl` | oui | Modèle de config iOS avec références `op://` |
| `ios/Config/Secrets.xcconfig` | **non** | Généré par `scripts/secrets/inject.sh` |
| `android/secrets.properties.tpl` | oui | Modèle de config Android avec références `op://` |
| `android/app/secrets.properties` | **non** | Généré par `scripts/secrets/inject.sh` ; lu par `android/app/build.gradle.kts` |
| `*.jks`, `*.keystore` | **non** | Clés de signature Android : ne jamais commiter |
| `*.p8`, `.env`, `.env.*` | **non** | Ne jamais commiter |

## Commandes

```bash
op signin                                   # une fois par session
scripts/secrets/setup-1password.sh          # crée le coffre et les items manquants (idempotent)
scripts/secrets/inject.sh                   # génère ios/Config/Secrets.xcconfig et android/app/secrets.properties
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

## App Android

`android/app/build.gradle.kts` injecte `SUPABASE_URL`, `SUPABASE_PUBLISHABLE_KEY` et `GOOGLE_WEB_CLIENT_ID` dans `BuildConfig`, dans cet ordre de priorité :

1. `android/app/secrets.properties` (généré par `scripts/secrets/inject.sh`, ignoré par git) ;
2. les variables d'environnement du même nom ;
3. des valeurs factices (`https://example.invalid`, `sb_publishable_dummy`, `dummy.apps.googleusercontent.com`). `A_RENSEIGNER` est traité comme absent.

Les valeurs factices permettent de compiler, tester et passer le lint sans aucun secret : c'est ce que fait la CI (job « Android », avec `https://ci.invalid` et `ci-dummy.apps.googleusercontent.com`). Elles sont refusées pour une diffusion : `assembleRelease` et `bundleRelease` échouent tant que la configuration est factice.

```bash
scripts/secrets/inject.sh                                  # une fois, après op signin
cd android && ./gradlew assembleDebug                      # build avec les vraies valeurs
```

## Connexion Google (Android)

L'app obtient un jeton d'identité Google via Credential Manager (`GetSignInWithGoogleOption`, `serverClientId` = client **Web**) puis le transmet à Supabase (`signInWithIdToken`, provider Google) avec un nonce : Google reçoit le SHA-256 du nonce, Supabase le nonce brut. Sans `GOOGLE_WEB_CLIENT_ID` réel, le bouton affiche « connexion pas encore configurée ».

Procédure, à faire une fois (console Google Cloud, projet dédié à l'app) :

1. **Écran de consentement OAuth** (API et services > Écran de consentement OAuth) : type *Externe*, nom de l'app, e-mail d'assistance, portées par défaut (`email`, `profile`, `openid`). Tant que l'app est en mode *Test*, ajouter chaque compte de la famille dans les utilisateurs de test, ou passer en production.
2. **Client OAuth « Application Web »** (Identifiants > Créer des identifiants > ID client OAuth). URI de redirection autorisé : `https://hbqihybpawkzhfbrtbyt.supabase.co/auth/v1/callback` (référence du projet : `project_id` de `[remotes.production]` dans `supabase/config.toml`). Noter l'ID client et le code secret.
3. **Clients OAuth « Android »**, un par empreinte, avec le nom de package `ch.jonathanlemaine.giftmanager` :
   - *debug* : SHA-1 du keystore de debug ;
   - *release* : SHA-1 de la clé de signature de l'app. Avec **Play App Signing**, utiliser la SHA-1 du *certificat de signature d'application* (Play Console > Intégrité de l'application > Signature d'application), et non celle de la clé d'upload. Ajouter aussi la SHA-1 de la clé d'upload si des builds installés hors du Play Store en dépendent.

   ```bash
   keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android -keypass android | grep SHA1
   ```

   Les clients Android n'ont pas de secret et ne sont pas lus par le code : Google les associe au package et à la SHA-1 lors de la demande. Leur absence ou une mauvaise SHA-1 se manifeste par un échec de connexion « générique » sur l'appareil.
4. **1Password** :

   ```bash
   scripts/secrets/setup-1password.sh          # crée l'item google-oauth s'il manque
   op item edit google-oauth --vault giftmanager "web_client_id[text]=<id>.apps.googleusercontent.com" \
     "android_client_id_debug[text]=<id>" "android_client_id_release[text]=<id>" \
     "sha1_debug[text]=<sha1>" "sha1_release[text]=<sha1>"
   op item edit google-oauth --vault giftmanager "web_client_secret[concealed]=<secret>"   # ne pas laisser dans l'historique du shell
   scripts/secrets/inject.sh && scripts/secrets/sync-github.sh
   ```
5. **Supabase** : le provider est déclaré dans `supabase/config.toml` (`[remotes.production.auth.external.google]`, `skip_nonce_check = false` : ne jamais cocher « Skip nonce checks »). Il lit `SUPABASE_AUTH_EXTERNAL_GOOGLE_CLIENT_ID` et `SUPABASE_AUTH_EXTERNAL_GOOGLE_SECRET`, alimentés par `.env.op` :

   ```bash
   op run --env-file=.env.op -- supabase config push --project-ref "$SUPABASE_PROJECT_REF"
   ```

   (ou, à la main : tableau de bord Supabase > Authentication > Sign In / Providers > Google, avec le client Web). En local, le provider reste désactivé.
6. Un build **release** refuse un `GOOGLE_WEB_CLIENT_ID` factice (`checkReleaseSecrets`).

## Tâches planifiées (pg_cron)

Les migrations planifient `birthday-reminders` (08:00 UTC), `track-prices` (05:00 UTC) et `purchase-reminders` (toutes les heures : seuls les profils pour qui il est 18 h localement sont servis). pg_cron appelle les fonctions Edge avec l'en-tête `x-cron-secret`. Deux secrets Vault sont à créer **une fois** sur la base de prod (sans eux l'appel est un no-op) :

```sql
-- éditeur SQL Supabase ; remplacer les deux valeurs (cron_secret : `op read op://giftmanager/cron/password`)
select vault.create_secret('https://<project_ref>.supabase.co/functions/v1', 'edge_functions_url');
select vault.create_secret('<valeur de op://giftmanager/cron/password>', 'cron_secret');
```

et le même secret côté fonctions : `scripts/secrets/sync-supabase.sh` pousse `CRON_SECRET` (item `cron`).

## Règles

- La `secret_key` (service_role) Supabase ne va **jamais** dans les apps iOS et Android : uniquement en CI et dans les Edge Functions.
- Les secrets passent par stdin ou par `op run`, jamais en argument de commande ni dans les logs.
- Supabase local (`supabase start`) utilise les clés de démo publiques de la CLI : rien à stocker.
- La CI exécute gitleaks sur chaque PR.
