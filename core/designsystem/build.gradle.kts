plugins {
    alias(libs.plugins.kstate.kmp.web)
    alias(libs.plugins.kstate.compose)
}

// Kstate's look: the theme, its colour tokens, and the generic pieces every screen is built from.
// Knows nothing about sessions or devices.
kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            api(libs.compose.material3)
        }
    }
}

// Compose UI tests need a webpack bundle on wasm, which a library can't declare (CMP-4906).
tasks
    .matching { it.name in setOf("checkComposeUiTestConfigurationForWasmJs", "wasmJsNodeTest") }
    .configureEach { enabled = false }
