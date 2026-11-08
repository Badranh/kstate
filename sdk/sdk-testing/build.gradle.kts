// Test support for SDK modules: a recording host the real SDK can connect to. Not published.
plugins {
    alias(libs.plugins.kstate.jvm.library)
}

dependencies {
    api(projects.core.protocol)
    api(projects.sdk.sdkCore)
    api(libs.kotlinx.coroutines.core)
    implementation(libs.ktor.server.cio)
    implementation(libs.ktor.server.websockets)
}
