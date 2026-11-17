package dev.kstate.buildlogic

/** Recognises Kstate's debug SDK among resolved dependencies, written as `project :path` or `group:module`. */
object KstateArtifacts {
    /** The Maven group every published Kstate module is under: Maven Central's namespace for the owner's GitHub account. */
    const val GROUP = "io.github.badranh"

    private val PUBLISHED_SDK_CORE = Regex("""^${Regex.escape(GROUP)}:sdk-core(-[a-z0-9]+)?$""")

    fun isSdkCore(component: String): Boolean =
        PUBLISHED_SDK_CORE.matches(component) || (component.startsWith("project ") && component.endsWith(":sdk-core"))

    /** Why a release classpath is not allowed, or null when it is clean. */
    fun releaseViolation(components: List<String>): String? {
        val offenders = components.filter(::isSdkCore)
        if (offenders.isEmpty()) return null
        return "Kstate's debug SDK (sdk-core) is in the release build's runtime classpath (${offenders.joinToString()}). " +
            "Use sdk-noop for release, or set kstateGuard { releaseUsesNoop = true } to swap it automatically."
    }
}
