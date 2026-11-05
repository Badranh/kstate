package dev.kstate.buildlogic

/** Recognises Kstate's debug SDK among resolved dependencies, written as `project :path` or `group:module`. */
object KstateArtifacts {
    private val PUBLISHED_SDK_CORE = Regex("""^dev\.kstate:sdk-core(-[a-z0-9]+)?$""")

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
