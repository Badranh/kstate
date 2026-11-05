plugins {
    alias(libs.plugins.kstate.kmp.web)
    alias(libs.plugins.kstate.compose)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.viewer.viewerClient)
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
        }
    }
}

// Compose UI tests run on the JVM. wasm UI tests need a webpack bundle, which a library
// can't declare (CMP-4906). Revisit with the M6 viewer-web spike.
tasks
    .matching { it.name in setOf("checkComposeUiTestConfigurationForWasmJs", "wasmJsNodeTest") }
    .configureEach { enabled = false }
