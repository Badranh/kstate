plugins {
    alias(libs.plugins.kstate.kmp.web)
    alias(libs.plugins.kstate.compose)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.core.data)
            implementation(projects.core.protocol)
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
        }
        commonTest.dependencies {
            implementation(libs.kotlinx.coroutines.test)
        }
        // Real Compose UI tests (gestures, keys, drawing) on the JVM, with Skia for this machine.
        jvmTest.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(compose.desktop.uiTestJUnit4)
        }
    }
}

// Compose UI tests run on the JVM. wasm UI tests need a webpack bundle, which a library
// can't declare (CMP-4906). Revisit with the M6 viewer-web spike.
tasks
    .matching { it.name in setOf("checkComposeUiTestConfigurationForWasmJs", "wasmJsNodeTest") }
    .configureEach { enabled = false }
