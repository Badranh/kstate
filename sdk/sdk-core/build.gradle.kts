plugins {
    alias(libs.plugins.kstate.kmp.library)
    alias(libs.plugins.kstate.kmp.sdk)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    android { namespace = "dev.kstate.sdk" }
    sourceSets {
        commonMain.dependencies {
            api(projects.protocol)
            api(libs.kotlinx.coroutines.core)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.websockets)
        }
        androidMain.dependencies {
            implementation(libs.ktor.client.cio)
        }
        jvmMain.dependencies {
            implementation(libs.ktor.client.cio)
        }
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }
        jvmTest.dependencies {
            implementation(libs.ktor.server.cio)
            implementation(libs.ktor.server.websockets)
        }
    }
}
