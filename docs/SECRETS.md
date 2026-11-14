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
| `apple-signin-web` | `services_id`, `client_id` (`Services ID,bundle iOS`, Services ID en premier), `key_id`, `team_id`, `private_key` (`.p8`), `client_secret` (JWT, 6 mois max), `enabled` | Connexion Apple web sur Android : provider Apple dans Supabase (`client_id` + `client_secret`), drapeau `APPLE_SIGN_IN_ENABLED` de l'app (`enabled`) |
| `android-upload` | `keystore` (fichier `.jks`), `keystore_password`, `key_alias`, `key_password`, `sha1` | Signature de l'AAB Android (clé d'**upload** Play), CI `android-release.yml` |
| `google-play` | `service_account_json`, `package_name` | Envoi sur la piste de test interne Google Play (CI) |
| `fcm` | `service_account` (JSON du compte de service Firebase), `project_id`, `api_key`, `app_id`, `sender_id` | Notifications push Android : `service_account` est poussé par `scripts/secrets/sync-supabase.sh` (secret `FCM_SERVICE_ACCOUNT`) ; `project_id`, `api_key`, `app_id`, `sender_id` sont lus par l'app Android au build (`FIREBASE_*`, pas de `google-services.json`) |
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
scripts/secrets/sync-supabase.sh            # pousse la clé APNs, CRON_SECRET et le compte de service FCM vers les secrets des fonctions Edge
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

`android/app/build.gradle.kts` injecte `SUPABASE_URL`, `SUPABASE_PUBLISHABLE_KEY`, `GOOGLE_WEB_CLIENT_ID` et `FIREBASE_API_KEY`, `FIREBASE_APP_ID`, `FIREBASE_PROJECT_ID`, `FIREBASE_SENDER_ID` dans `BuildConfig` (ainsi que le drapeau `APPLE_SIGN_IN_ENABLED`, `false` par défaut, voir « Connexion Apple sur Android (web) »), dans cet ordre de priorité :

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

## Connexion Apple sur Android (web)

Un compte créé avec Apple sur iPhone s'ouvre sur Android par le flux OAuth web d'Apple via Supabase : l'app ouvre un Custom Tab (`signInWith(Apple)`, PKCE), Apple renvoie vers Supabase (`https://hbqihybpawkzhfbrtbyt.supabase.co/auth/v1/callback`), puis Supabase redirige vers `giftmanager://login-callback?code=…` ; `MainActivity` échange le code contre une session (`handleDeeplinks`). C'est le même compte Supabase que sur iOS. Tant que `APPLE_SIGN_IN_ENABLED` n'est pas `true`, le bouton affiche « La connexion Apple n'est pas encore configurée… » (option, y compris en release).

Procédure, à faire une fois (compte Apple Developer, <https://developer.apple.com/account>) :

1. **Services ID** (Certificates, Identifiers & Profiles > Identifiers > « + » > Services IDs) : identifiant `ch.jonathanlemaine.giftmanager.web`. L'activer puis *Sign in with Apple > Configure* : *Primary App ID* = l'App ID de l'app iOS, **Domains** = `hbqihybpawkzhfbrtbyt.supabase.co`, **Return URLs** = `https://hbqihybpawkzhfbrtbyt.supabase.co/auth/v1/callback` (référence du projet : `project_id` de `[remotes.production]` dans `supabase/config.toml`).
2. **Clé Sign in with Apple** (Keys > « + », cocher *Sign in with Apple*, App ID principal = l'app iOS) : télécharger le fichier `.p8` (une seule fois), noter le *Key ID*. Le *Team ID* est celui de l'item `apple-developer`.
3. **Secret client** : Apple n'accepte qu'un JWT signé (ES256) avec cette clé, valable **6 mois au maximum** : `iss` = Team ID, `sub` = Services ID, `aud` = `https://appleid.apple.com`, `kid` = Key ID. Générer le JWT (outil de la documentation Supabase « Login with Apple » ou tout script ES256), puis le stocker. **Mettre un rappel dans 5 mois : à l'expiration, la connexion Apple Android cesse de fonctionner (iOS natif non concerné).**
4. **1Password** (item `apple-signin-web`, jamais de valeur dans le dépôt ni dans l'historique du shell) :

   ```bash
   scripts/secrets/setup-1password.sh          # crée l'item apple-signin-web s'il manque
   op item edit apple-signin-web --vault giftmanager "services_id[text]=ch.jonathanlemaine.giftmanager.web" \
     "client_id[text]=ch.jonathanlemaine.giftmanager.web,ch.jonathanlemaine.giftmanager" \
     "key_id[text]=<key id>" "team_id[text]=<team id>"
   op item edit apple-signin-web --vault giftmanager "private_key[concealed]=<contenu du .p8>" "client_secret[concealed]=<jwt>"
   ```
5. **Supabase** : `[remotes.production.auth.external.apple]` lit `SUPABASE_AUTH_EXTERNAL_APPLE_CLIENT_ID` (`Services ID,bundle iOS` : Supabase envoie le **premier** identifiant à Apple pour le flux web, et dans l'autre ordre Apple répond `invalid_request` ; la connexion native iOS accepte les deux, dans n'importe quel ordre) et `SUPABASE_AUTH_EXTERNAL_APPLE_SECRET` (le JWT), alimentés par `.env.op` ; `giftmanager://login-callback` est dans `additional_redirect_urls` (local et production) :

   ```bash
   op run --env-file=.env.op -- supabase config push --project-ref "$SUPABASE_PROJECT_REF"
   ```

   (ou, à la main : Authentication > Sign In / Providers > Apple, *Client IDs* et *Secret Key*, puis Authentication > URL Configuration > Redirect URLs.) En local, le provider garde le seul bundle ID : `supabase start` n'a besoin d'aucune variable.
6. **Activer le bouton** : `op item edit apple-signin-web --vault giftmanager "enabled[text]=true"`, puis `scripts/secrets/inject.sh && scripts/secrets/sync-github.sh` (secret GitHub `APPLE_SIGN_IN_ENABLED`, lu par le workflow de publication Android).

Le drapeau `APPLE_SIGN_IN_ENABLED` suit la même priorité que les autres valeurs (fichier, variable d'environnement, défaut `false`).

## Publication Google Play (test interne)

Le tag `vX.Y.Z` posé par `scripts/release.sh` déclenche `.github/workflows/android-release.yml` : AAB signé, `versionName` = tag, `versionCode` = minutes depuis 2026-01-01 (`scripts/android/version-code.sh`), envoi sur la piste **test interne**. Le build lit la signature dans les variables d'environnement `ANDROID_KEYSTORE_PATH`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD` ; sans elles, `bundleRelease` échoue avec un message explicite (`checkReleaseSigning`).

Secrets GitHub créés par `scripts/secrets/sync-github.sh` : `FIREBASE_API_KEY`, `FIREBASE_APP_ID`, `FIREBASE_PROJECT_ID`, `FIREBASE_SENDER_ID`, `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD`, `PLAY_SERVICE_ACCOUNT_JSON`, `SUPABASE_URL` (plus `SUPABASE_PUBLISHABLE_KEY` et `GOOGLE_WEB_CLIENT_ID`, déjà synchronisés).

### Deux clés distinctes (Play App Signing)

- **Clé d'upload** : celle de l'item `android-upload`. Elle ne sert qu'à prouver à Google que l'AAB vient de nous. Si elle est perdue ou compromise, Google permet de la réinitialiser.
- **Clé de signature** : générée et gardée par Google. C'est elle qui signe l'APK installé sur les téléphones. Sa SHA-1 (Play Console > Intégrité de l'application > Signature d'application) est celle à déclarer dans le client OAuth Android *release* (item `google-oauth`, champs `android_client_id_release` et `sha1_release`, voir « Connexion Google »). Déclarer la SHA-1 de la clé d'upload ne suffit pas : la connexion Google échouerait sur l'app installée depuis Play.

### Actions humaines (une fois)

1. **Compte Play Console** : créer un compte développeur (frais uniques de 25 USD ; un compte personnel créé après nov. 2023 exige un test fermé de 12 testeurs pendant 14 jours avant la production, mais **pas** pour le test interne).
2. **Créer l'application** : nom « Gift Manager » (comme sur iOS, #125), langue par défaut, type Application, gratuite, nom de package `ch.jonathanlemaine.giftmanager` (définitif).
3. **Fiche et déclarations** (Tableau de bord > Configurer l'application) : description, icône 512 px, visuel de présentation, captures d'écran ; classification du contenu ; public cible (ne pas cibler les enfants : l'app est destinée aux adultes, le « mode enfant » n'est qu'un affichage) ; déclaration d'annonces (aucune) ; **sécurité des données** (Data safety) : e-mail, nom, contenus créés par l'utilisateur (idées, cadeaux, photos), identifiants ; données chiffrées en transit, suppression de compte possible.
4. **Politique de confidentialité publique** : publier `docs/privacy` à une URL publique (GitHub Pages ou autre) et la renseigner dans la fiche (Contenu de l'application > Politique de confidentialité).
5. **Clé d'upload** (hors du dépôt, dans un dossier temporaire) :

   ```bash
   keytool -genkeypair -v -keystore upload.jks -alias upload -keyalg RSA -keysize 2048 -validity 10000
   keytool -list -v -keystore upload.jks -alias upload | grep SHA1
   scripts/secrets/setup-1password.sh                      # crée android-upload et google-play s'ils manquent
   op item edit android-upload --vault giftmanager "keystore[file]=upload.jks" "key_alias[text]=upload" "sha1[text]=<sha1>"
   op item edit android-upload --vault giftmanager "keystore_password[concealed]=<mot de passe>" "key_password[concealed]=<mot de passe>"   # ne pas laisser dans l'historique du shell
   shred -u upload.jks 2>/dev/null || rm -P upload.jks     # le fichier ne doit pas traîner
   ```
6. **Play App Signing** : à la création de la première version, accepter l'inscription à Play App Signing (par défaut) ; Google signe l'app avec sa propre clé. Relever ensuite la SHA-1 du certificat de signature et la déclarer dans le client OAuth Android *release* (voir ci-dessus).
7. **Compte de service pour la CI** : dans Google Cloud (projet de l'app), activer l'API *Google Play Android Developer*, créer un compte de service et une clé JSON ; dans la Play Console > Utilisateurs et autorisations, inviter l'e-mail du compte de service avec l'accès à l'application et les droits de publication sur les pistes de test (« Publier les applications sur les pistes de test »). Puis :

   ```bash
   op item edit google-play --vault giftmanager "service_account_json[concealed]=$(cat service-account.json)"
   rm service-account.json
   ```
8. **Piste de test interne** : Tests > Test interne > Testeurs : créer une liste (adresses Gmail de la famille), copier le lien d'inscription et l'envoyer aux testeurs.
9. **Premier envoi** : l'API de Play refuse de publier sur une app qui n'a jamais reçu de version par la console. Envoyer **une fois à la main** un AAB signé (artefact d'un lancement `gh workflow run android-release.yml -f publish=false`, ou build local) dans Test interne > Créer une version, puis publier. Ensuite le workflow suffit.
10. **Synchroniser et publier** : `scripts/secrets/sync-github.sh`, puis `scripts/release.sh X.Y.Z`.

Le workflow envoie la version en `status: draft` : à valider dans la Play Console (Test interne > Versions) pour la rendre disponible aux testeurs. Une fois la première version publiée par ce chemin sans accroc, passer `status` à `completed` dans `android-release.yml` pour une mise à disposition automatique.

## Notifications push Android

Les appareils Android reçoivent leurs notifications via Firebase Cloud Messaging (API HTTP v1). Les fonctions Edge envoient des messages « data-only » avec un compte de service Google ; sans le secret `FCM_SERVICE_ACCOUNT`, seuls les appareils iOS (APNs) sont servis, et inversement.

1. Dans la console Firebase, créer le projet Firebase **dans le même projet Google Cloud** que la connexion Google (voir ci-dessus), pour ne gérer qu'un seul projet.
2. Y enregistrer l'app Android `ch.jonathanlemaine.giftmanager`. Il n'y a **pas** de `google-services.json` à télécharger : l'app initialise Firebase elle-même (`FirebaseApp.initializeApp` avec `FirebaseOptions`) depuis `BuildConfig`. Relever seulement quatre valeurs (Paramètres du projet > Général > Vos applications, ou le `google-services.json` consulté puis supprimé) : `project_id` (ID du projet), `api_key` (`current_key`), `app_id` (`mobilesdk_app_id`, de la forme `1:123…:android:abc…`) et `sender_id` (numéro du projet, `project_number`).
3. Dans Google Cloud, créer un compte de service avec le rôle **Firebase Cloud Messaging API Admin**, puis générer une clé JSON. Vérifier que l'**API Firebase Cloud Messaging (V1)** est activée.
4. Stocker les valeurs dans 1Password (`scripts/secrets/setup-1password.sh` crée l'item `fcm` avec `A_RENSEIGNER`) :

   ```bash
   op item edit fcm --vault giftmanager "service_account[concealed]=$(cat ~/Downloads/<cle>.json)"
   op item edit fcm --vault giftmanager "project_id[text]=..." "app_id[text]=..." "sender_id[text]=..."
   op item edit fcm --vault giftmanager "api_key[concealed]=..."   # ne pas laisser dans l'historique du shell
   rm ~/Downloads/<cle>.json                  # le fichier ne doit pas traîner sur le disque
   ```

5. Pousser le secret vers Supabase : `scripts/secrets/sync-supabase.sh` (JSON compacté, jamais affiché).
6. Côté app : `scripts/secrets/inject.sh` écrit `FIREBASE_API_KEY`, `FIREBASE_APP_ID`, `FIREBASE_PROJECT_ID` et `FIREBASE_SENDER_ID` dans `android/app/secrets.properties` (même ordre de priorité que Supabase : fichier, variables d'environnement, valeurs factices `dummy`) ; `scripts/secrets/sync-github.sh` les pousse vers GitHub Actions pour le build de diffusion. Avec des valeurs factices ou absentes, l'app compile et tourne, mais les notifications push sont désactivées (Firebase n'est pas initialisé) ; `assembleRelease` et `bundleRelease` refusent une configuration Firebase factice (`checkReleaseSecrets`).
7. Android 13 et plus : la permission `POST_NOTIFICATIONS` est demandée à l'utilisateur après sa première connexion ; le jeton FCM n'est enregistré côté serveur (`register_device`, plateforme `android`) qu'une fois les notifications autorisées, et supprimé à la déconnexion.

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
