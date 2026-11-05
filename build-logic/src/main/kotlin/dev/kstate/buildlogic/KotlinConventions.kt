package dev.kstate.buildlogic

import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

internal val KSTATE_JVM_TARGET = JvmTarget.JVM_17

internal fun Project.configureKmpTestDependencies(kotlin: KotlinMultiplatformExtension) {
    kotlin.sourceSets.getByName("commonTest").dependencies {
        implementation(libs.library("kotlin-test"))
        implementation(libs.library("kotlinx-coroutines-test"))
        implementation(libs.library("turbine"))
    }
}

internal fun Project.useJUnitPlatformForTests() {
    tasks.withType<Test>().configureEach { useJUnitPlatform() }
}

/** Adds the JVM target used for fast common tests and for host-side consumers. */
fun KotlinMultiplatformExtension.kstateJvm() {
    jvm {
        compilerOptions { jvmTarget.set(KSTATE_JVM_TARGET) }
    }
}

/** Adds the iOS targets. Only macOS hosts can build them. */
fun KotlinMultiplatformExtension.kstateIos() {
    iosArm64()
    iosSimulatorArm64()
}

/**
 * Adds the wasmJs target. Libraries run their tests on Node so no browser is needed;
 * the browser environment is declared so browser apps can consume them.
 */
@OptIn(ExperimentalWasmDsl::class)
fun KotlinMultiplatformExtension.kstateWasm() {
    wasmJs {
        browser { testTask { enabled = false } }
        nodejs()
    }
}
