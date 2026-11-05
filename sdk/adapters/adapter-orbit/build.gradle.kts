plugins {
    alias(libs.plugins.kstate.kmp.library)
    alias(libs.plugins.kstate.kmp.sdk)
}

kotlin {
    android { namespace = "dev.kstate.adapter.orbit" }
    sourceSets {
        commonMain.dependencies {
            api(projects.sdk.sdkCore)
            api(libs.orbit.core)
        }
        jvmTest.dependencies {
            implementation(projects.sdk.sdkTesting)
        }
    }
}
