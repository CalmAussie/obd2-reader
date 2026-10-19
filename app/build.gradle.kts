import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Release signing. The key must stay the same for every build, or phones refuse to install an
// update over an older version. It comes from environment variables (set by the GitHub workflow
// from repository secrets) or from a local keystore.properties file that is never committed.
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun signingValue(env: String, prop: String): String? =
    System.getenv(env)?.takeIf { it.isNotBlank() } ?: keystoreProps.getProperty(prop)

val releaseStoreFile = signingValue("OBD_KEYSTORE_FILE", "storeFile")?.let { rootProject.file(it) }?.takeIf { it.exists() }

// Every GitHub build gets a higher version code, so a newer APK installs as an update.
// The version name comes from gradle.properties and matches CHANGELOG.md.
val buildNumber = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1
val appVersion = (findProperty("obdVersion") as String?) ?: "1.0"

android {
    namespace = "com.obdreader.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.obdreader.app"
        minSdk = 26
        targetSdk = 34
        versionCode = buildNumber
        versionName = appVersion
    }

    signingConfigs {
        if (releaseStoreFile != null) {
            create("release") {
                storeFile = releaseStoreFile
                storePassword = signingValue("OBD_KEYSTORE_PASSWORD", "storePassword")
                keyAlias = signingValue("OBD_KEY_ALIAS", "keyAlias")
                keyPassword = signingValue("OBD_KEY_PASSWORD", "keyPassword")
                // Every signature scheme, so any installer or file manager accepts the APK.
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // Without the release key (no secrets set up yet), fall back to the debug key so the
            // build still produces an installable APK. Updates then need an uninstall first.
            signingConfig = if (releaseStoreFile != null) signingConfigs.getByName("release") else signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // Android Auto (Car App Library)
    implementation("androidx.car.app:app:1.4.0")
    implementation("androidx.car.app:app-projected:1.4.0")

    testImplementation("junit:junit:4.13.2")
}
