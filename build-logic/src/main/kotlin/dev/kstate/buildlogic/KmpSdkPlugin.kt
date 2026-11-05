package dev.kstate.buildlogic

import kotlinx.validation.ApiValidationExtension
import kotlinx.validation.ExperimentalBCVApi
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

/**
 * Public, published KMP API: explicit API mode and binary-compatibility dumps.
 * Apply next to a target plugin such as kstate.kmp-library.
 */
class KmpSdkPlugin : Plugin<Project> {
    @OptIn(ExperimentalBCVApi::class)
    override fun apply(target: Project) =
        with(target) {
            pluginManager.withPlugin("org.jetbrains.kotlin.multiplatform") {
                extensions.configure<KotlinMultiplatformExtension> { explicitApi() }
            }
            pluginManager.apply("org.jetbrains.kotlinx.binary-compatibility-validator")
            extensions.configure<ApiValidationExtension> {
                klib { enabled = true }
            }
        }
}
