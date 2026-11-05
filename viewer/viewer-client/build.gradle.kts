plugins {
    alias(libs.plugins.kstate.kmp.web)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.protocol)
            api(libs.kotlinx.coroutines.core)
        }
    }
}
