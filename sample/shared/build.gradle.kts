plugins {
    alias(libs.plugins.kstate.kmp.library)
    alias(libs.plugins.kstate.compose)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    android { namespace = "dev.kstate.sample.shared" }

    listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.binaries.framework {
            baseName = "SampleShared"
            isStatic = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(projects.sdk.sdkCore)
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.ui)
            implementation(libs.compose.material3)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.lifecycle.viewmodel)
            implementation(libs.lifecycle.viewmodel.compose)
            implementation(libs.orbit.core)
            api(libs.decompose.core)
            implementation(libs.decompose.compose)
            api(libs.essenty.lifecycle)
        }
    }
}
