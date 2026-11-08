plugins {
    alias(libs.plugins.kstate.jvm.library)
}

// Finding adb and talking to it: devices, port forwarding, files and processes on the device.
dependencies {
    api(libs.kotlinx.coroutines.core)
}
