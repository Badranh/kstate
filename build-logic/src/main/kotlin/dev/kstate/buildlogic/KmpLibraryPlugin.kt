package dev.kstate.buildlogic

import com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryTarget
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

/** KMP library for apps under test: Android, iOS, plus JVM for fast tests. */
class KmpLibraryPlugin : Plugin<Project> {
    override fun apply(target: Project) =
        with(target) {
            pluginManager.apply("kstate.kmp-android-library")
            extensions.configure<KotlinMultiplatformExtension> {
                kstateIos()
                kstateJvm()
            }
        }
}

/** KMP library with only the Android target, for Android-only adapters. */
class KmpAndroidLibraryPlugin : Plugin<Project> {
    override fun apply(target: Project) =
        with(target) {
            pluginManager.apply("org.jetbrains.kotlin.multiplatform")
            pluginManager.apply("com.android.kotlin.multiplatform.library")
            pluginManager.apply("kstate.lint")
            useJUnitPlatformForTests()
            extensions.configure<KotlinMultiplatformExtension> {
                targets.withType<KotlinMultiplatformAndroidLibraryTarget>().configureEach {
                    compileSdk = libs.version("android-compileSdk").toInt()
                    minSdk = libs.version("android-minSdk").toInt()
                    compilerOptions { jvmTarget.set(KSTATE_JVM_TARGET) }
                    withHostTest {}
                }
                configureKmpTestDependencies(this)
            }
        }
}

/** KMP library for the viewer side: JVM (desktop) and wasmJs (browser). */
class KmpWebPlugin : Plugin<Project> {
    override fun apply(target: Project) =
        with(target) {
            pluginManager.apply("org.jetbrains.kotlin.multiplatform")
            pluginManager.apply("kstate.lint")
            useJUnitPlatformForTests()
            extensions.configure<KotlinMultiplatformExtension> {
                kstateJvm()
                kstateWasm()
                configureKmpTestDependencies(this)
            }
        }
}
