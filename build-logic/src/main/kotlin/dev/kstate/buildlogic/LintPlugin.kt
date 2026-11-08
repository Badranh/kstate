package dev.kstate.buildlogic

import dev.detekt.gradle.Detekt
import dev.detekt.gradle.extensions.DetektExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.withType
import org.jlleitschuh.gradle.ktlint.KtlintExtension
import org.jlleitschuh.gradle.ktlint.reporter.ReporterType

class LintPlugin : Plugin<Project> {
    override fun apply(target: Project) =
        with(target) {
            pluginManager.apply("dev.detekt")
            pluginManager.apply("org.jlleitschuh.gradle.ktlint")
            configureDetekt()
            configureKtlint()
            runSourceSetDetektOnCheck()
        }

    /** KMP modules get one detekt task per source set; the plain `detekt` task sees none of them. */
    private fun Project.runSourceSetDetektOnCheck() {
        pluginManager.withPlugin("base") {
            tasks.named("check").configure {
                dependsOn(tasks.withType<Detekt>().matching { it.isSourceSetDetekt() })
            }
        }
    }

    private fun Task.isSourceSetDetekt(): Boolean =
        name.startsWith("detekt") && name.endsWith("SourceSet") && !name.startsWith("detektBaseline")

    private fun Project.configureDetekt() {
        extensions.configure<DetektExtension> {
            toolVersion.set(libs.version("detekt"))
            buildUponDefaultConfig.set(true)
            parallel.set(true)
            config.setFrom(rootProject.files("config/detekt/detekt.yml"))
            basePath.set(rootProject.projectDir)
        }
        // Generated code (Compose's Res accessors, say) is not ours to style, as ktlint already skips it.
        tasks.withType<Detekt>().configureEach {
            exclude { it.file.invariantSeparatorsPath.contains("/build/") }
        }
    }

    private fun Project.configureKtlint() {
        extensions.configure<KtlintExtension> {
            version.set(libs.version("ktlint-engine"))
            outputToConsole.set(true)
            reporters { reporter(ReporterType.PLAIN) }
            filter {
                exclude { it.file.invariantSeparatorsPath.contains("/build/") }
            }
        }
    }
}
