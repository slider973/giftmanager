import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// Configuration Supabase (valeurs publiques côté client), par ordre de priorité :
// (idem pour GOOGLE_WEB_CLIENT_ID, l'ID client OAuth « Web » utilisé comme serverClientId de Credential Manager,
// et pour FIREBASE_API_KEY / FIREBASE_APP_ID / FIREBASE_PROJECT_ID / FIREBASE_SENDER_ID, la configuration Firebase
// des notifications push : pas de plugin google-services, l'app initialise Firebase elle-même depuis BuildConfig, #76)
//   1. app/secrets.properties, généré par scripts/secrets/inject.sh depuis 1Password (ignoré par git) ;
//   2. variables d'environnement SUPABASE_URL / SUPABASE_PUBLISHABLE_KEY (CI) ;
//   3. valeurs factices, qui suffisent à compiler et tester mais font échouer le build release.
val dummyUrl = "https://example.invalid"
val dummyKey = "sb_publishable_dummy"
val dummyGoogleClientId = "dummy.apps.googleusercontent.com"
val dummyFirebaseValue = "dummy"

val secrets = Properties().apply {
    val file = layout.projectDirectory.file("secrets.properties").asFile
    if (file.exists()) file.inputStream().use { load(it) }
}

// A_RENSEIGNER : valeur d'attente de 1Password (scripts/secrets/setup-1password.sh), traitée comme absente.
fun String.isUsable() = isNotBlank() && this != "A_RENSEIGNER"

fun secret(name: String, fallback: String): String =
    secrets.getProperty(name)?.takeIf { it.isUsable() }
        ?: providers.environmentVariable(name).orNull?.takeIf { it.isUsable() }
        ?: fallback

val supabaseUrl = secret("SUPABASE_URL", dummyUrl)
val supabaseKey = secret("SUPABASE_PUBLISHABLE_KEY", dummyKey)
val googleWebClientId = secret("GOOGLE_WEB_CLIENT_ID", dummyGoogleClientId)
val firebaseApiKey = secret("FIREBASE_API_KEY", dummyFirebaseValue)
val firebaseAppId = secret("FIREBASE_APP_ID", dummyFirebaseValue)
val firebaseProjectId = secret("FIREBASE_PROJECT_ID", dummyFirebaseValue)
val firebaseSenderId = secret("FIREBASE_SENDER_ID", dummyFirebaseValue)

// Clé d'upload Play (release), uniquement par variables d'environnement : en CI elles viennent des secrets
// GitHub, en local de `op run`. Absentes, le release n'est pas signé (et bundleRelease refuse, voir plus bas).
// Jamais lues depuis un fichier suivi par git, jamais écrites dans un log.
fun signingEnv(name: String): String? =
    providers.environmentVariable(name).orNull?.takeIf { it.isUsable() }

val keystorePath = signingEnv("ANDROID_KEYSTORE_PATH")
val keystorePassword = signingEnv("ANDROID_KEYSTORE_PASSWORD")
val keyAliasName = signingEnv("ANDROID_KEY_ALIAS")
val keyPasswordValue = signingEnv("ANDROID_KEY_PASSWORD")
val hasReleaseSigning = keystorePath != null && keystorePassword != null &&
    keyAliasName != null && keyPasswordValue != null

android {
    namespace = "ch.jonathanlemaine.giftmanager"
    compileSdk = 36

    defaultConfig {
        // Aligné sur PRODUCT_BUNDLE_IDENTIFIER côté iOS (ios/project.yml).
        applicationId = "ch.jonathanlemaine.giftmanager"
        minSdk = 26
        targetSdk = 36
        versionName = providers.gradleProperty("versionName").getOrElse("0.0.0-dev")
        versionCode = providers.gradleProperty("versionCode").map(String::toInt).getOrElse(1)

        buildConfigField("String", "SUPABASE_URL", "\"$supabaseUrl\"")
        buildConfigField("String", "SUPABASE_PUBLISHABLE_KEY", "\"$supabaseKey\"")
        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"$googleWebClientId\"")
        buildConfigField("String", "FIREBASE_API_KEY", "\"$firebaseApiKey\"")
        buildConfigField("String", "FIREBASE_APP_ID", "\"$firebaseAppId\"")
        buildConfigField("String", "FIREBASE_PROJECT_ID", "\"$firebaseProjectId\"")
        buildConfigField("String", "FIREBASE_SENDER_ID", "\"$firebaseSenderId\"")
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    lint {
        abortOnError = true
        warningsAsErrors = false
    }

    // Le fichier de contrôle des empreintes audio n'a rien à faire dans l'APK (outil de génération, #66).
    androidResources {
        ignoreAssetsPattern = "!.svn:!.git:!.ds_store:!*.scc:.*:<dir>_*:!CVS:!thumbs.db:!picasa.ini:!*~:!fingerprints.json"
    }

    sourceSets {
        // Père Noël vocal (#78) : script et répliques audio partagés avec iOS, sans copie. Les tests lisent le vrai JSON.
        val santaScript = rootProject.layout.projectDirectory.dir("../ios/GiftManager/Resources/SantaScript")
        getByName("main").assets.srcDir(santaScript)
        getByName("test").resources.srcDir(santaScript)
        // Fixtures JSON partagées avec le reste du dépôt (supabase/fixtures/json).
        getByName("test").resources.srcDir(rootProject.layout.projectDirectory.dir("../supabase/fixtures/json"))
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(keystorePath!!)
                storePassword = keystorePassword
                keyAlias = keyAliasName
                keyPassword = keyPasswordValue
            }
        }
    }

    buildTypes {
        release {
            if (hasReleaseSigning) signingConfig = signingConfigs.getByName("release")
            // R8 reste désactivé pour la première publication : l'app dépend de supabase-kt, Ktor,
            // kotlinx-serialization et Coil (réflexion, services chargés dynamiquement), et un build
            // minifié non éprouvé sur appareil risquerait de casser la production sans avertissement.
            // À prévoir ensuite : activer isMinifyEnabled + isShrinkResources, ajouter dans
            // proguard-rules.pro les règles de supabase-kt/Ktor/OkHttp si besoin, puis valider un AAB
            // minifié sur appareil (connexion, realtime, photos) avant de le publier.
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    // Icônes Material de base ; les manquantes sont des vector drawables (res/drawable/ic_*.xml).
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.googleid)

    implementation(libs.androidx.datastore.preferences)

    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

    implementation(platform(libs.supabase.bom))
    implementation(libs.supabase.auth)
    implementation(libs.supabase.postgrest)
    implementation(libs.supabase.realtime)
    implementation(libs.supabase.storage)
    implementation(libs.supabase.functions)
    implementation(libs.ktor.client.okhttp)

    // Aperçu de lien (#73) : HTTP explicite, analyse HTML et orientation EXIF des photos choisies.
    implementation(libs.okhttp)
    implementation(libs.jsoup)
    implementation(libs.androidx.exifinterface)

    // Mode enfant (#75) : empreinte, visage ou code de l'appareil à la sortie (BiometricPrompt, stable 1.1.0).
    implementation(libs.androidx.biometric)

    // Père Noël vocal (#78) : lecture des répliques audio livrées (pas de media3-ui : l'écran est en Compose).
    implementation(libs.androidx.media3.exoplayer)

    // Widget d'écran d'accueil (#80) : rendu Glance, rafraîchi périodiquement par WorkManager.
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.work.runtime.ktx)

    // Notifications push (#76) : FCM seul, initialisé à la main depuis BuildConfig (pas de plugin google-services).
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.kotlinx.coroutines.test)
}

// Garde : un build de diffusion ne doit jamais embarquer les valeurs factices.
val checkReleaseSecrets by tasks.registering {
    group = "verification"
    description = "Échoue si la configuration Supabase, Google ou Firebase est factice (build release)."
    val url = supabaseUrl
    val key = supabaseKey
    val googleId = googleWebClientId
    val firebaseValues = mapOf(
        "FIREBASE_API_KEY" to firebaseApiKey,
        "FIREBASE_APP_ID" to firebaseAppId,
        "FIREBASE_PROJECT_ID" to firebaseProjectId,
        "FIREBASE_SENDER_ID" to firebaseSenderId,
    )
    doLast {
        if (url == dummyUrl || key == dummyKey || url.endsWith(".invalid") || key.contains("dummy")) {
            throw GradleException(
                "Configuration Supabase factice : lance scripts/secrets/inject.sh (voir docs/SECRETS.md) avant un build release."
            )
        }
        if (googleId == dummyGoogleClientId || googleId.contains("dummy")) {
            throw GradleException(
                "GOOGLE_WEB_CLIENT_ID factice : renseigne op://giftmanager/google-oauth/web_client_id puis lance scripts/secrets/inject.sh (voir docs/SECRETS.md)."
            )
        }
        val dummyFirebase = firebaseValues.filterValues { it == dummyFirebaseValue || it.contains("dummy") }.keys
        if (dummyFirebase.isNotEmpty()) {
            throw GradleException(
                "Configuration Firebase factice (${dummyFirebase.joinToString()}) : renseigne les champs de l'item op://giftmanager/fcm " +
                    "puis lance scripts/secrets/inject.sh (voir docs/SECRETS.md)."
            )
        }
    }
}

// Garde : un AAB destiné à Google Play doit être signé avec la clé d'upload (sinon Play le refuse).
val checkReleaseSigning by tasks.registering {
    group = "verification"
    description = "Échoue si la clé d'upload Play n'est pas fournie ou introuvable (bundleRelease)."
    val configured = hasReleaseSigning
    val path = keystorePath
    doLast {
        if (!configured) {
            throw GradleException(
                "Signature release absente : définis ANDROID_KEYSTORE_PATH, ANDROID_KEYSTORE_PASSWORD, " +
                    "ANDROID_KEY_ALIAS et ANDROID_KEY_PASSWORD (clé d'upload, voir docs/SECRETS.md)."
            )
        }
        if (!file(path!!).isFile) {
            throw GradleException("Keystore introuvable à l'emplacement ANDROID_KEYSTORE_PATH.")
        }
    }
}

tasks.configureEach {
    if (name == "assembleRelease" || name == "bundleRelease") dependsOn(checkReleaseSecrets)
    if (name == "bundleRelease") dependsOn(checkReleaseSigning)
}
