plugins {
    alias(libs.plugins.kstate.kmp.android.library)
    alias(libs.plugins.kstate.kmp.sdk)
}

kotlin {
    android { namespace = "dev.kstate.adapter.livedata" }
    sourceSets {
        commonMain.dependencies {
            api(projects.protocol)
        }
    }
}
