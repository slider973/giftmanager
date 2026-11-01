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
