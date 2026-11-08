plugins {
    alias(libs.plugins.kstate.kmp.web)
    alias(libs.plugins.kstate.compose)
}

// The inspector: devices, their sources and states, the editor and comments, laid out in one
// screen that the desktop host and the web viewer both show.
kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.core.ui)
            implementation(projects.core.protocol)
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
        }
        commonTest.dependencies {
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

// wasm UI tests need a webpack bundle, which a library can't declare (CMP-4906).
tasks
    .matching { it.name in setOf("checkComposeUiTestConfigurationForWasmJs", "wasmJsNodeTest") }
    .configureEach { enabled = false }
