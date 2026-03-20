// Top-level build file
plugins {
    // Используем alias из каталога libs.versions.toml
    alias(libs.plugins.android.application) apply false
    id("com.android.library") version "9.0.1" apply false

    // Вместо id(...).version(...) используем централизованную версию
    alias(libs.plugins.kotlin.compose) apply false

    // Google services
    id("com.google.gms.google-services") version "4.4.1" apply false
}