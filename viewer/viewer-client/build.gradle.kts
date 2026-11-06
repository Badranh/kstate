plugins {
    alias(libs.plugins.kstate.kmp.web)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.protocol)
            api(libs.kotlinx.coroutines.core)
        }
    }
}
