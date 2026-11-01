import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// Configuration Supabase (valeurs publiques côté client), par ordre de priorité :
// (idem pour GOOGLE_WEB_CLIENT_ID, l'ID client OAuth « Web » utilisé comme serverClientId de Credential Manager)
//   1. app/secrets.properties, généré par scripts/secrets/inject.sh depuis 1Password (ignoré par git) ;
//   2. variables d'environnement SUPABASE_URL / SUPABASE_PUBLISHABLE_KEY (CI) ;
//   3. valeurs factices, qui suffisent à compiler et tester mais font échouer le build release.
val dummyUrl = "https://example.invalid"
val dummyKey = "sb_publishable_dummy"
val dummyGoogleClientId = "dummy.apps.googleusercontent.com"

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

    sourceSets {
        // Fixtures JSON partagées avec le reste du dépôt (supabase/fixtures/json).
        getByName("test").resources.srcDir(rootProject.layout.projectDirectory.dir("../supabase/fixtures/json"))
    }

    buildTypes {
        release {
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

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}

// Garde : un build de diffusion ne doit jamais embarquer les valeurs factices.
val checkReleaseSecrets by tasks.registering {
    group = "verification"
    description = "Échoue si la configuration Supabase ou Google est factice (build release)."
    val url = supabaseUrl
    val key = supabaseKey
    val googleId = googleWebClientId
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
    }
}

tasks.configureEach {
    if (name == "assembleRelease" || name == "bundleRelease") dependsOn(checkReleaseSecrets)
}
