plugins {
    alias(libs.plugins.kstate.jvm.library)
}

dependencies {
    implementation(projects.protocol)
    api(projects.host.hostCore)
}
