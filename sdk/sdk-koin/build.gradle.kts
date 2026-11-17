// Empty until the Koin helpers are built (an open question in docs/SPEC.md), so it is neither
// published nor API-checked: an empty artifact would only mislead.
plugins {
    alias(libs.plugins.kstate.kmp.library)
}

kotlin {
    android { namespace = "dev.kstate.sdk.koin" }
    sourceSets {
        commonMain.dependencies {
            api(projects.core.protocol)
        }
    }
}
