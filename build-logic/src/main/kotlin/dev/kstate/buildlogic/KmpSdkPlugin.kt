package dev.kstate.buildlogic

import com.vanniktech.maven.publish.MavenPublishBaseExtension
import kotlinx.validation.ApiValidationExtension
import kotlinx.validation.ExperimentalBCVApi
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

/**
 * Public, published KMP API: explicit API mode, binary-compatibility dumps, and Maven coordinates
 * `dev.kstate:<module name>` at the catalog's `kstate` version. Apply next to a target plugin such
 * as kstate.kmp-library.
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
            publish()
        }

    // Only coordinates and a POM for now: no signing and no Maven Central until the project has a
    // namespace it can publish under there. publishToMavenLocal works as it is.
    private fun Project.publish() {
        pluginManager.apply("com.vanniktech.maven.publish")
        // Taken out here: inside pom {} a bare `name` is the POM's own property, not the module's.
        val module = name
        extensions.configure<MavenPublishBaseExtension> {
            coordinates(GROUP, module, libs.version("kstate"))
            pom {
                name.set("Kstate $module")
                description.set("Kstate's debug SDK for Kotlin Multiplatform apps: $module.")
                inceptionYear.set("2026")
                licenses {
                    license {
                        name.set("The Apache License, Version 2.0")
                        url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                        distribution.set("repo")
                    }
                }
            }
        }
    }

    private companion object {
        const val GROUP = "dev.kstate"
    }
}
