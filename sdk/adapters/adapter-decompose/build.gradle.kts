plugins {
    alias(libs.plugins.kstate.kmp.library)
    alias(libs.plugins.kstate.kmp.sdk)
}

kotlin {
    android { namespace = "dev.kstate.adapter.decompose" }
    sourceSets {
        commonMain.dependencies {
            api(projects.protocol)
        }
    }
}
