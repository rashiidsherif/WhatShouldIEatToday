// Top-level build file where you can add configuration options common to all sub-projects/modules.
// AGP 9 has built-in Kotlin support, so no org.jetbrains.kotlin.android plugin.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
