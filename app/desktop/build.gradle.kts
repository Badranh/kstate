import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kstate.jvm.app)
}

dependencies {
    implementation(projects.host.server)
    implementation(projects.host.video)
    implementation(projects.host.tunnel)
    implementation(projects.feature.agent)
    implementation(projects.feature.inspector)
    implementation(compose.desktop.currentOs)
    implementation(libs.compose.material3)
    implementation(libs.kotlinx.coroutines.swing)
    implementation(libs.koin.core)
}

// The web viewer the host serves to guests: the wasm bundle, under viewer/ on the classpath.
tasks.named<ProcessResources>("processResources") {
    dependsOn(":app:web:wasmJsBrowserDistribution")
    from(project(":app:web").layout.buildDirectory.dir("dist/wasmJs/productionExecutable")) {
        into("viewer")
        include("*.html", "*.css", "*.js", "*.wasm", "*.svg", "composeResources/**")
    }
}

compose.desktop {
    application {
        mainClass = "dev.kstate.app.desktop.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Dmg)
            packageName = "Kstate"
            packageVersion = "0.1.0"
            macOS {
                bundleID = "dev.kstate.host"
                iconFile.set(project.file("icons/kstate.icns"))
                // jpackage rejects macOS versions starting with 0; see docs/DECISIONS.md.
                packageVersion = "1.0.0"
            }
        }
    }
}
