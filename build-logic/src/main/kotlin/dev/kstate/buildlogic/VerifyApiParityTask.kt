package dev.kstate.buildlogic

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

/**
 * Fails when two modules' binary-compatibility dumps differ. sdk-noop must expose exactly
 * sdk-core's public API so that swapping one for the other never breaks a build.
 */
abstract class VerifyApiParityTask : DefaultTask() {
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val referenceApi: DirectoryProperty

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val candidateApi: DirectoryProperty

    @TaskAction
    fun verify() {
        val reference = dumps(referenceApi)
        val candidate = dumps(candidateApi)
        val mismatches =
            (reference.keys + candidate.keys).filter { kind -> reference[kind] != candidate[kind] }
        if (mismatches.isNotEmpty()) {
            throw GradleException(
                "Public API differs from ${referenceApi.get().asFile} in: ${mismatches.joinToString()}. " +
                    "Run apiDump on both modules and make them match.",
            )
        }
    }

    /** Dump contents by kind (jvm, klib), with the module's own name taken out. */
    private fun dumps(directory: DirectoryProperty): Map<String, List<String>> =
        directory
            .get()
            .asFile
            .walkTopDown()
            .filter { it.isFile && it.name.endsWith(".api") }
            .associate { file ->
                val kind = if (file.name.endsWith(".klib.api")) "klib" else file.parentFile.name
                kind to file.readLines().filterNot { it.startsWith("// Library unique name") }
            }
}
