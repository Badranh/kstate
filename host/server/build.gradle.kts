plugins {
    alias(libs.plugins.kstate.jvm.library)
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    api(projects.host.adb)
    api(projects.core.protocol)
    api(projects.core.session)
    api(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.cio)
    implementation(libs.ktor.server.websockets)
    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.ktor.client.cio)
    testImplementation(libs.ktor.client.websockets)
}
