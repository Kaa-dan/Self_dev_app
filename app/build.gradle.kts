import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

// Firebase (Google Sign-In + cloud backup) switches on once app/google-services.json lists this app.
// The file is shared with the full app; until Lite is added to the Firebase project it only has the
// full app's package, which the plugin would reject, so the app then builds without Firebase.
val firebaseConfig = file("google-services.json")
if (firebaseConfig.exists() && firebaseConfig.readText().contains("\"com.nithinraj.worktrack.simple\"")) {
    apply(plugin = "com.google.gms.google-services")
}

android {
    namespace = "com.wac.worktrack"
    compileSdk = 37

    defaultConfig {
        // Own id so the simple app installs next to the full WorkTrack app instead of replacing it.
        applicationId = "com.nithinraj.worktrack.simple"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"
    }

    // Release signing key, read from keystore-lite.properties in the project root (git-ignored).
    // Lite has its own key, separate from the full app's keystore.properties.
    // Without that file the release APK is built unsigned.
    val keystoreProps = rootProject.file("keystore-lite.properties")
    if (keystoreProps.exists()) {
        val props = Properties().apply { keystoreProps.inputStream().use { load(it) } }
        signingConfigs.create("release") {
            storeFile = file(props.getProperty("storeFile"))
            storePassword = props.getProperty("storePassword")
            keyAlias = props.getProperty("keyAlias")
            keyPassword = props.getProperty("keyPassword")
        }
    }

    buildTypes {
        getByName("release") {
            signingConfigs.findByName("release")?.let { signingConfig = it }
            // R8 strips unused code (most of material-icons-extended) and unused resources.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

// Room writes each database version's schema here, so migrations can be checked against it.
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation("androidx.core:core-ktx:1.17.0")
    // Installs the Compose libraries' baseline profiles, so cold start isn't run interpreted.
    implementation("androidx.profileinstaller:profileinstaller:1.4.0")

    // Compose UI
    implementation(platform("androidx.compose:compose-bom:2026.09.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended:1.7.8")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.0")

    // Local database
    implementation("androidx.room:room-runtime:2.8.5")
    implementation("androidx.room:room-ktx:2.8.5")
    ksp("androidx.room:room-compiler:2.8.5")

    // Daily cloud backup
    implementation("androidx.work:work-runtime-ktx:2.12.0")

    // Firebase + Google Sign-In for the cloud backup
    implementation(platform("com.google.firebase:firebase-bom:35.1.0"))
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")
    implementation("androidx.credentials:credentials:1.6.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.6.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.2.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.11.0")
    // Play services auth pulls in an old Fragment that breaks permission requests; pin a fixed one.
    implementation("androidx.fragment:fragment-ktx:1.9.1")

    testImplementation("junit:junit:4.13.2")
}
