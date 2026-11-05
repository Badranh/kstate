package dev.kstate.buildlogic

import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

/** JVM-only library on the host side, tested with JUnit 5. */
class JvmLibraryPlugin : Plugin<Project> {
    override fun apply(target: Project) =
        with(target) {
            pluginManager.apply("org.jetbrains.kotlin.jvm")
            pluginManager.apply("kstate.lint")
            extensions.configure<JavaPluginExtension> {
                sourceCompatibility = JavaVersion.VERSION_17
                targetCompatibility = JavaVersion.VERSION_17
            }
            extensions.configure<KotlinJvmProjectExtension> {
                compilerOptions { jvmTarget.set(KSTATE_JVM_TARGET) }
            }
            useJUnitPlatformForTests()
            dependencies {
                add("testImplementation", libs.library("kotlin-test"))
                add("testImplementation", libs.library("kotlinx-coroutines-test"))
                add("testImplementation", libs.library("turbine"))
                add("testImplementation", libs.library("junit-jupiter"))
                add("testRuntimeOnly", libs.library("junit-platform-launcher"))
            }
        }
}

/** Compose Desktop application on the JVM. */
class JvmAppPlugin : Plugin<Project> {
    override fun apply(target: Project) =
        with(target) {
            pluginManager.apply("kstate.jvm-library")
            pluginManager.apply("kstate.compose")
        }
}
