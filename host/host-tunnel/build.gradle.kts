plugins {
    alias(libs.plugins.kstate.jvm.library)
}

dependencies {
    implementation(projects.protocol)
}
