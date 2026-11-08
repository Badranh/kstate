package dev.kstate.host.adb

/** Everything the host does with adb: device discovery and the video pipeline's tools. */
interface Adb :
    AdbClient,
    AdbDeviceTools
