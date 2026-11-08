import dev.kstate.buildlogic.kstateWasm

plugins {
    alias(libs.plugins.kstate.kmp.library)
    alias(libs.plugins.kstate.kmp.sdk)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    kstateWasm()
    android { namespace = "dev.kstate.core.protocol" }
    sourceSets {
        commonMain.dependencies {
            api(libs.kotlinx.serialization.json)
        }
    }
}
