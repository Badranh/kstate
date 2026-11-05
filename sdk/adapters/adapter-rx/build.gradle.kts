import dev.kstate.buildlogic.kstateJvm

plugins {
    alias(libs.plugins.kstate.kmp.android.library)
    alias(libs.plugins.kstate.kmp.sdk)
}

kotlin {
    // RxJava is plain JVM, so the adapter also builds for the JVM, which is where its test runs.
    kstateJvm()
    android { namespace = "dev.kstate.adapter.rx" }
    sourceSets {
        commonMain.dependencies {
            api(projects.sdk.sdkCore)
        }
        jvmMain {
            kotlin.srcDir("src/jvmCommonMain/kotlin")
            dependencies {
                api(libs.rxjava)
                implementation(libs.kotlinx.coroutines.rx3)
            }
        }
        androidMain {
            kotlin.srcDir("src/jvmCommonMain/kotlin")
            dependencies {
                api(libs.rxjava)
                implementation(libs.kotlinx.coroutines.rx3)
            }
        }
        jvmTest.dependencies {
            implementation(projects.sdk.sdkTesting)
        }
    }
}
