plugins {
    alias(libs.plugins.kstate.kmp.library)
    alias(libs.plugins.kstate.kmp.sdk)
}

kotlin {
    android { namespace = "dev.kstate.adapter.mvikotlin" }
    sourceSets {
        commonMain.dependencies {
            api(projects.sdk.sdkCore)
            api(libs.mvikotlin.core)
            implementation(libs.mvikotlin.coroutines)
        }
        jvmTest.dependencies {
            implementation(projects.sdk.sdkTesting)
            implementation(libs.mvikotlin.main)
        }
    }
}
