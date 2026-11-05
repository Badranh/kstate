plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.kmp.library) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.compose.multiplatform) apply false
}

// build-logic is an included build: run its tests (e.g. the release guard) as part of check.
apply(plugin = "base")
tasks.named("check") {
    dependsOn(gradle.includedBuild("build-logic").task(":test"))
}
