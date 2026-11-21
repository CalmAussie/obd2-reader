plugins {
    id("com.android.application") version "9.5.2" apply false
    // Since AGP 9 the Android plugin compiles Kotlin itself, so the app doesn't apply this plugin.
    // Listing it here only sets the Kotlin version the Android plugin uses.
    id("org.jetbrains.kotlin.android") version "2.4.21" apply false
}
