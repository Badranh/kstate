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
            implementation(libs.kotlin.reflect)
        }
        jvmMain.dependencies {
            implementation(libs.ktor.client.cio)
            implementation(libs.kotlin.reflect)
        }
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }
        // Reflection-based code is shared by the two JVM targets, Android and the desktop JVM.
        jvmMain { kotlin.srcDir("src/jvmCommonMain/kotlin") }
        androidMain { kotlin.srcDir("src/jvmCommonMain/kotlin") }
        jvmTest { kotlin.srcDir("src/jvmCommonTest/kotlin") }
        named("androidHostTest") { kotlin.srcDir("src/jvmCommonTest/kotlin") }
        jvmTest.dependencies {
            implementation(libs.ktor.server.cio)
            implementation(libs.ktor.server.websockets)
        }
    }
}
