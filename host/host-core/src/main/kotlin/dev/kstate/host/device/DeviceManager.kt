package dev.kstate.host.device

import dev.kstate.host.adb.AdbClient
import dev.kstate.host.adb.AdbDevice
import dev.kstate.host.adb.AdbException
import dev.kstate.host.gateway.Gateway
import dev.kstate.host.gateway.GatewayFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.IOException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

data class AttachedDevice(
    val serial: String,
    val model: String?,
    /** The host port this device's apps reach through `adb reverse`. */
    val hostPort: Int,
)

/**
 * Keeps one gateway and one `adb reverse` rule per ready Android device, polling adb, and puts a
 * lost rule back.
 * Connections through adb reverse all come from 127.0.0.1, so the port identifies the device.
 */
class DeviceManager(
    private val adb: AdbClient,
    private val ports: PortAllocator,
    private val gateways: GatewayFactory,
    private val devicePort: Int = APP_PORT,
    private val pollInterval: Duration = 2.seconds,
) {
    private class Attachment(
        val device: AttachedDevice,
        val gateway: Gateway,
    )

    private val attachments = linkedMapOf<String, Attachment>()
    private val attached = MutableStateFlow<List<AttachedDevice>>(emptyList())
    private val lastProblem = MutableStateFlow<String?>(null)

    val devices: StateFlow<List<AttachedDevice>> = attached.asStateFlow()

    /** The last adb failure, cleared by the next successful poll. */
    val problem: StateFlow<String?> = lastProblem.asStateFlow()

    fun start(scope: CoroutineScope): Job =
        scope.launch {
            while (isActive) {
                poll()
                delay(pollInterval)
            }
        }

    suspend fun poll() {
        val ready =
            try {
                adb.devices().filter(AdbDevice::isReady).associateBy(AdbDevice::serial)
            } catch (failure: AdbException) {
                lastProblem.value = failure.message
                return
            }
        lastProblem.value = null
        (attachments.keys - ready.keys).forEach { detach(it) }
        val known = attachments.keys.toList()
        (ready.keys - attachments.keys).forEach { attach(ready.getValue(it)) }
        known.forEach { keepReversed(it) }
        publish()
    }

    suspend fun detachAll() {
        attachments.keys.toList().forEach { detach(it) }
        publish()
    }

    private suspend fun attach(device: AdbDevice) {
        val port = ports.allocate()
        val gateway =
            try {
                gateways.start(device.serial, port)
            } catch (failure: IOException) {
                // Another program took the port between the check and the bind: try again next poll.
                ports.release(port)
                lastProblem.value = "Couldn't listen for ${device.serial}'s app on port $port: ${failure.message}"
                return
            }
        try {
            adb.reverse(device.serial, devicePort, port)
            attachments[device.serial] = Attachment(AttachedDevice(device.serial, device.model, port), gateway)
        } catch (failure: AdbException) {
            gateway.stop()
            ports.release(port)
            lastProblem.value = failure.message
        }
    }

    /**
     * The app's state reaches us through the reverse rule, and adb drops every rule when its server
     * restarts, even while the device stays listed. Put it back, so the state channel returns as
     * video does.
     */
    private suspend fun keepReversed(serial: String) {
        val attachment = attachments[serial] ?: return
        try {
            if (devicePort !in adb.reversedPorts(serial)) adb.reverse(serial, devicePort, attachment.device.hostPort)
        } catch (failure: AdbException) {
            lastProblem.value = failure.message
        }
    }

    private suspend fun detach(serial: String) {
        val attachment = attachments.remove(serial) ?: return
        attachment.gateway.stop()
        try {
            adb.removeReverse(serial, devicePort)
        } catch (_: AdbException) {
            // The device is usually gone already, and its rules with it.
        }
        ports.release(attachment.device.hostPort)
    }

    private fun publish() {
        attached.value = attachments.values.map { it.device }
    }

    companion object {
        /** The port the SDK connects to on the device. */
        const val APP_PORT: Int = 47000
    }
}
