plugins {
    alias(libs.plugins.kstate.kmp.web)
    alias(libs.plugins.kotlin.serialization)
}

// What the viewer shows: plain data, no behaviour. Every layer above reads these.
kotlin {
    sourceSets {
        commonMain.dependencies {
            api(libs.kotlinx.serialization.json)
        }
    }
}
