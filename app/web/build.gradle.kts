import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kstate.compose)
    alias(libs.plugins.kstate.lint)
}

kotlin {
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser {
            commonWebpackConfig { outputFileName = "kstate-web.js" }
        }
        binaries.executable()
    }
    sourceSets {
        wasmJsMain.dependencies {
            implementation(projects.feature.inspector)
            implementation(projects.core.session)
            implementation(libs.compose.ui)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.kotlinx.serialization.json)
            implementation(compose.components.resources)
        }
    }
}

// The fonts the viewer falls back to for what Compose's default lacks (emoji, CJK, symbols).
compose.resources {
    packageOfResClass = "dev.kstate.app.web.resources"
    publicResClass = false
    // The app has no commonMain, where the plugin looks for the library to decide on its own.
    generateResClass = always
}
