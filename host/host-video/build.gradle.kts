plugins {
    alias(libs.plugins.kstate.jvm.library)
}

dependencies {
    implementation(projects.protocol)
    api(projects.host.hostCore)
    implementation(libs.bytedeco.ffmpeg)
    implementation(libs.bytedeco.javacpp)
    runtimeOnly(variantOf(libs.bytedeco.ffmpeg) { classifier(nativeClassifier) })
    runtimeOnly(variantOf(libs.bytedeco.javacpp) { classifier(nativeClassifier) })
}

/** Apple silicon first (docs/SPEC.md: arm64 first, x64 second). LGPL: never a "-gpl" classifier. */
val nativeClassifier = "macosx-arm64"
