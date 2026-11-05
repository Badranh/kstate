package dev.kstate.buildlogic

import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.DependencySubstitutions
import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.component.ModuleComponentSelector
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.api.artifacts.component.ProjectComponentSelector
import org.gradle.api.artifacts.result.ResolvedComponentResult
import org.gradle.api.artifacts.result.ResolvedDependencyResult
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.register

interface KstateGuardExtension {
    /** Release builds get sdk-noop wherever sdk-core was requested, so shared code and adapters still link. */
    val releaseUsesNoop: Property<Boolean>
}

/**
 * The build half of "the SDK is debug-only": every release variant of an Android app fails to build
 * while sdk-core is on its runtime classpath. The runtime half is Kstate.start()'s debuggable check.
 */
class GuardPlugin : Plugin<Project> {
    override fun apply(target: Project) =
        with(target) {
            val guard = extensions.create<KstateGuardExtension>("kstateGuard").apply { releaseUsesNoop.convention(false) }
            pluginManager.withPlugin("com.android.application") {
                configurations.configureEach {
                    if (RELEASE_CLASSPATH.matches(name)) {
                        resolutionStrategy.dependencySubstitution { swapToNoopWhen(guard.releaseUsesNoop) }
                    }
                }
                registerReleaseChecks()
            }
        }

    private fun DependencySubstitutions.swapToNoopWhen(enabled: Property<Boolean>) {
        val substitutions = this
        all {
            if (!enabled.get()) return@all
            when (val wanted = requested) {
                is ProjectComponentSelector ->
                    if (wanted.projectPath.endsWith(":sdk-core")) useTarget(substitutions.project(wanted.projectPath.replaceAfterLast(':', "sdk-noop")))
                is ModuleComponentSelector ->
                    if (KstateArtifacts.isSdkCore("${wanted.group}:${wanted.module}")) {
                        useTarget(substitutions.module("${wanted.group}:${wanted.module.replace("sdk-core", "sdk-noop")}:${wanted.version}"))
                    }
            }
        }
    }

    private fun Project.registerReleaseChecks() {
        val components = extensions.getByType<ApplicationAndroidComponentsExtension>()
        components.onVariants(components.selector().withBuildType("release")) { variant ->
            val variantName = variant.name.replaceFirstChar { it.uppercase() }
            val runtime = configurations.named("${variant.name}RuntimeClasspath")
            val verify =
                tasks.register<VerifyNoSdkCoreTask>("verifyKstate$variantName") {
                    group = "verification"
                    description = "Fails if Kstate's debug SDK is in the ${variant.name} build."
                    resolvedComponents.set(runtime.flatMap { it.incoming.resolutionResult.rootComponent }.map(::allComponentIds))
                }
            tasks.matching { it.name == "pre${variantName}Build" }.configureEach { dependsOn(verify) }
        }
    }

    private companion object {
        val RELEASE_CLASSPATH = Regex("""(^release|Release)\w*(Runtime|Compile)Classpath$""")

        fun allComponentIds(root: ResolvedComponentResult): List<String> {
            val seen = linkedSetOf<String>()
            val queue = ArrayDeque(listOf(root))
            while (queue.isNotEmpty()) {
                val component = queue.removeFirst()
                if (!seen.add(component.id.label())) continue
                component.dependencies.filterIsInstance<ResolvedDependencyResult>().forEach { queue += it.selected }
            }
            return seen.toList()
        }

        fun ComponentIdentifier.label(): String =
            when (this) {
                is ProjectComponentIdentifier -> "project $projectPath"
                is ModuleComponentIdentifier -> "$group:$module"
                else -> displayName
            }
    }
}

abstract class VerifyNoSdkCoreTask : DefaultTask() {
    @get:Input
    abstract val resolvedComponents: ListProperty<String>

    @TaskAction
    fun verify() {
        KstateArtifacts.releaseViolation(resolvedComponents.get())?.let { throw GradleException(it) }
    }
}
