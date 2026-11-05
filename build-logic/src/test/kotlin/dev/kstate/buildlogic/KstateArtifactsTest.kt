package dev.kstate.buildlogic

import kotlin.test.Test
import kotlin.test.assertEquals

class KstateArtifactsTest {
    @Test
    fun recognisesTheSdkCoreProjectAndItsPublishedArtifacts() {
        assertEquals(
            listOf(true, true, true, true),
            listOf(
                KstateArtifacts.isSdkCore("project :sdk:sdk-core"),
                KstateArtifacts.isSdkCore("dev.kstate:sdk-core"),
                KstateArtifacts.isSdkCore("dev.kstate:sdk-core-android"),
                KstateArtifacts.isSdkCore("dev.kstate:sdk-core-jvm"),
            ),
        )
    }

    @Test
    fun leavesTheNoopAndEverythingElseAlone() {
        assertEquals(
            listOf(false, false, false, false),
            listOf(
                KstateArtifacts.isSdkCore("project :sdk:sdk-noop"),
                KstateArtifacts.isSdkCore("dev.kstate:sdk-noop-android"),
                KstateArtifacts.isSdkCore("dev.kstate:adapter-orbit"),
                KstateArtifacts.isSdkCore("com.example:sdk-core"),
            ),
        )
    }

    @Test
    fun explainsWhichDependencyBroughtItIn() {
        assertEquals(
            "Kstate's debug SDK (sdk-core) is in the release build's runtime classpath (project :sdk:sdk-core). " +
                "Use sdk-noop for release, or set kstateGuard { releaseUsesNoop = true } to swap it automatically.",
            KstateArtifacts.releaseViolation(listOf("project :sample:shared", "project :sdk:sdk-core")),
        )
        assertEquals(null, KstateArtifacts.releaseViolation(listOf("project :sdk:sdk-noop")))
    }
}
