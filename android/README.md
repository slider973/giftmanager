# Android — Famille Cadeaux

Application Android (Kotlin, Jetpack Compose, Material 3) de Famille Cadeaux. Même backend Supabase que l'app iOS.

## Prérequis

- JDK 17 et Android SDK (API 36) ; `android/local.properties` avec `sdk.dir=...` (ignoré par git).
- Optionnel : la CLI 1Password pour les vraies valeurs Supabase (voir `docs/SECRETS.md`).

## Commandes

```bash
cd android
./gradlew assembleDebug testDebugUnitTest lintDebug   # ce que fait la CI
./gradlew assembleRelease                               # refusé tant que la config Supabase est factice
```

Sans secret, le build utilise des valeurs factices : il compile et les tests passent, mais l'app ne joint pas Supabase.
Pour de vraies valeurs : `scripts/secrets/inject.sh` génère `app/secrets.properties`.

## Publication Google Play

Le tag `vX.Y.Z` (`scripts/release.sh`) lance `.github/workflows/android-release.yml` : `bundleRelease` signé avec la clé d'upload, envoi sur la piste de test interne. La signature est lue dans `ANDROID_KEYSTORE_PATH`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD` ; sans elles `bundleRelease` échoue (`checkReleaseSigning`), `assembleDebug` est inchangé.

```bash
# après scripts/secrets/inject.sh et l'export des 4 variables de signature
cd android && ./gradlew bundleRelease -PversionName=1.1.0 -PversionCode=$(../scripts/android/version-code.sh)
../scripts/android/version-code_test.sh   # test du calcul de versionCode
```

R8 est désactivé pour la première publication (`isMinifyEnabled = false`, voir le commentaire dans `app/build.gradle.kts`). Procédure complète et actions humaines : `docs/SECRETS.md`.

## Architecture

Calquée sur l'app iOS, avec la logique séparée de l'affichage :

| Dossier (`app/src/main/kotlin/ch/jonathanlemaine/giftmanager`) | Rôle | iOS |
|---|---|---|
| `core/model`, `core/format`, `core/config` | Modèles, formatage, configuration — Kotlin pur, testable sur la JVM | `Core/` |
| `data/` | Client et repository Supabase | `Services/` |
| `ui/` | Écrans Compose (déclaratifs) et ViewModels | `Views/` |

Injection de dépendances manuelle (`AppContainer`). Navigation Compose avec routes typées (`ui/navigation`).

## Tests

Tests unitaires JVM uniquement (`app/src/test`). Les fixtures JSON sont partagées dans `supabase/fixtures/json/`.
