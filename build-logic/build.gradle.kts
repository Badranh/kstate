plugins {
    `kotlin-dsl`
}

dependencies {
    implementation(libs.gradle.kotlin)
    implementation(libs.gradle.kotlin.compose)
    implementation(libs.gradle.kotlin.serialization)
    implementation(libs.gradle.compose)
    implementation(libs.gradle.android)
    implementation(libs.gradle.detekt)
    implementation(libs.gradle.ktlint)
    implementation(libs.gradle.bcv)
}

gradlePlugin {
    plugins {
        register("lint") {
            id = "kstate.lint"
            implementationClass = "dev.kstate.buildlogic.LintPlugin"
        }
        register("kmpLibrary") {
            id = "kstate.kmp-library"
            implementationClass = "dev.kstate.buildlogic.KmpLibraryPlugin"
        }
        register("kmpAndroidLibrary") {
            id = "kstate.kmp-android-library"
            implementationClass = "dev.kstate.buildlogic.KmpAndroidLibraryPlugin"
        }
        register("kmpWeb") {
            id = "kstate.kmp-web"
            implementationClass = "dev.kstate.buildlogic.KmpWebPlugin"
        }
        register("kmpSdk") {
            id = "kstate.kmp-sdk"
            implementationClass = "dev.kstate.buildlogic.KmpSdkPlugin"
        }
        register("compose") {
            id = "kstate.compose"
            implementationClass = "dev.kstate.buildlogic.ComposePlugin"
        }
        register("jvmLibrary") {
            id = "kstate.jvm-library"
            implementationClass = "dev.kstate.buildlogic.JvmLibraryPlugin"
        }
        register("jvmApp") {
            id = "kstate.jvm-app"
            implementationClass = "dev.kstate.buildlogic.JvmAppPlugin"
        }
    }
}
