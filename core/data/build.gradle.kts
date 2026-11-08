plugins {
    alias(libs.plugins.kstate.kmp.web)
}

// Where a viewer gets a session from: the contracts the desktop host, the web viewer and the
// agent implement or read, so the UI depends on neither.
kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.core.model)
            api(libs.kotlinx.coroutines.core)
        }
    }
}
