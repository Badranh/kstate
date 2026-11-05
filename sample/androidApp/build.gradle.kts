plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kstate.compose)
    alias(libs.plugins.kstate.lint)
    alias(libs.plugins.kstate.guard)
}

// Release builds get sdk-noop. -Pkstate.demo.sdkCoreInRelease shows the guard failing without the swap.
kstateGuard {
    releaseUsesNoop = !providers.gradleProperty("kstate.demo.sdkCoreInRelease").isPresent
}

android {
    namespace = "dev.kstate.sample.android"
    compileSdk =
        libs.versions.android.compileSdk
            .get()
            .toInt()

    defaultConfig {
        applicationId = "dev.kstate.sample"
        minSdk =
            libs.versions.android.minSdk
                .get()
                .toInt()
        targetSdk =
            libs.versions.android.targetSdk
                .get()
                .toInt()
        versionCode = 1
        versionName = "0.1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions { jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17 }
}

dependencies {
    implementation(projects.sample.shared)
    implementation(projects.sdk.sdkCore)
    implementation(libs.androidx.activity.compose)
}
