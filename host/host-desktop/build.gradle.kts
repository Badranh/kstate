import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kstate.jvm.app)
}

dependencies {
    implementation(projects.host.hostCore)
    implementation(projects.viewer.viewerUi)
    implementation(compose.desktop.currentOs)
    implementation(libs.kotlinx.coroutines.swing)
    implementation(libs.koin.core)
}

compose.desktop {
    application {
        mainClass = "dev.kstate.host.desktop.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Dmg)
            packageName = "Kstate"
            packageVersion = "0.1.0"
            macOS {
                bundleID = "dev.kstate.host"
                // jpackage rejects macOS versions starting with 0; see docs/DECISIONS.md.
                packageVersion = "1.0.0"
            }
        }
    }
}
