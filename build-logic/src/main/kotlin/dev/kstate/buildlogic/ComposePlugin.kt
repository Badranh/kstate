package dev.kstate.buildlogic

import org.gradle.api.Plugin
import org.gradle.api.Project

class ComposePlugin : Plugin<Project> {
    override fun apply(target: Project) =
        with(target) {
            pluginManager.apply("org.jetbrains.compose")
            pluginManager.apply("org.jetbrains.kotlin.plugin.compose")
        }
}
