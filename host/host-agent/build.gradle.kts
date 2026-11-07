plugins {
    alias(libs.plugins.kstate.jvm.library)
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    api(projects.viewer.viewerClient)
    api(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.mcp.server)
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.cio)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mcp.client)
    testImplementation(libs.mcp.testing)
    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.ktor.client.cio)
}
