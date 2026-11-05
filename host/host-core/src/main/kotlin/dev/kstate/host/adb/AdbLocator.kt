package dev.kstate.host.adb

import java.io.File

/**
 * Finds the user's adb: ANDROID_HOME, then ANDROID_SDK_ROOT, then PATH, then Android Studio's
 * default SDK on macOS (an app started from Finder doesn't see the shell's PATH).
 */
class AdbLocator(
    private val env: (String) -> String? = System::getenv,
    private val isExecutable: (File) -> Boolean = File::canExecute,
) {
    fun locate(): File? = candidates().firstOrNull(isExecutable)

    private fun candidates(): Sequence<File> =
        sequence {
            env("ANDROID_HOME")?.let { yield(sdkAdb(it)) }
            env("ANDROID_SDK_ROOT")?.let { yield(sdkAdb(it)) }
            env("PATH")?.split(File.pathSeparator)?.forEach { yield(File(it, ADB)) }
            env("HOME")?.let { yield(sdkAdb("$it/Library/Android/sdk")) }
        }

    private fun sdkAdb(sdk: String) = File(File(sdk, "platform-tools"), ADB)

    private companion object {
        const val ADB = "adb"
    }
}
