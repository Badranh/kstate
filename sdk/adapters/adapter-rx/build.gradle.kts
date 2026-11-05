plugins {
    alias(libs.plugins.kstate.kmp.android.library)
    alias(libs.plugins.kstate.kmp.sdk)
}

kotlin {
    android { namespace = "dev.kstate.adapter.rx" }
    sourceSets {
        commonMain.dependencies {
            api(projects.protocol)
        }
    }
}
