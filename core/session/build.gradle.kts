plugins {
    alias(libs.plugins.kstate.kmp.web)
    alias(libs.plugins.kotlin.serialization)
}

// The session model shared by the desktop host and the web viewer: the engine that turns app
// messages into a session, its timeline, diffs, and how a session maps to what the viewer shows.
kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.core.data)
            api(projects.core.protocol)
            implementation(libs.kotlinx.serialization.json)
        }
        commonTest.dependencies {
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
