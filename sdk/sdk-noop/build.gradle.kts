import dev.kstate.buildlogic.VerifyApiParityTask

plugins {
    alias(libs.plugins.kstate.kmp.library)
    alias(libs.plugins.kstate.kmp.sdk)
    // Same generated classes as sdk-core (e.g. Sensitive${'$'}Impl for the @SerialInfo annotation).
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    android { namespace = "dev.kstate.sdk.noop" }
    sourceSets {
        commonMain.dependencies {
            api(projects.protocol)
            api(libs.kotlinx.coroutines.core)
        }
    }
}

val verifyApiParity by tasks.registering(VerifyApiParityTask::class) {
    description = "Checks that sdk-noop exposes exactly the public API of sdk-core."
    group = "verification"
    referenceApi.set(project(":sdk:sdk-core").layout.projectDirectory.dir("api"))
    candidateApi.set(layout.projectDirectory.dir("api"))
}

tasks.named("check") { dependsOn(verifyApiParity) }
