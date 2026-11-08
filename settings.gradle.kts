rootProject.name = "kstate"
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    includeBuild("build-logic")
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
    }
}

include(":core:protocol")
include(":core:model")
include(":core:data")
include(":core:session")

include(":sdk:sdk-core")
include(":sdk:sdk-noop")
include(":sdk:sdk-testing")
include(":sdk:sdk-koin")
include(":sdk:adapters:adapter-orbit")
include(":sdk:adapters:adapter-mvikotlin")
include(":sdk:adapters:adapter-decompose")
include(":sdk:adapters:adapter-livedata")
include(":sdk:adapters:adapter-rx")

include(":host:host-core")
include(":host:host-video")
include(":host:host-tunnel")
include(":host:host-agent")
include(":host:host-desktop")

include(":viewer:viewer-ui")
include(":viewer:viewer-web")

include(":sample:shared")
include(":sample:androidApp")
