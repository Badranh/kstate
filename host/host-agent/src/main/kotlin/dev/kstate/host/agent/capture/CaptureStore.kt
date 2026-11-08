package dev.kstate.host.agent.capture

import dev.kstate.core.model.SessionView
import dev.kstate.host.agent.image.Fingerprint
import dev.kstate.host.agent.image.Fingerprints
import dev.kstate.host.agent.image.Picture
import dev.kstate.host.agent.image.Pngs
import dev.kstate.host.agent.settle.Settled
import kotlinx.serialization.json.JsonElement

/** A settled screen and what produced it: the route and every source's state on that device. */
class Capture(
    val id: String,
    val deviceId: String,
    val route: List<String>,
    val states: Map<String, JsonElement?>,
    val png: ByteArray,
    val width: Int,
    val height: Int,
    val fingerprint: Fingerprint,
    val blank: Boolean,
    val settled: Boolean,
)

/**
 * Every screen the agent took, by id, newest kept. Pictures are kept as PNG, not raw pixels, so a
 * long session stays small. Thread-safe.
 */
class CaptureStore(
    private val max: Int = DEFAULT_MAX,
) {
    private val captures = LinkedHashMap<String, Capture>()
    private var next = 1

    @Synchronized
    fun take(
        view: SessionView,
        deviceId: String,
        settled: Settled,
    ): Capture? {
        val frame = settled.frame ?: return null
        val picture = Picture.of(frame)
        val device = view.devices.firstOrNull { it.id == deviceId }
        val capture =
            Capture(
                id = "c${next++}",
                deviceId = deviceId,
                route = device?.route.orEmpty(),
                states =
                    device
                        ?.sources
                        .orEmpty()
                        .filter { it.live }
                        .associate { it.key to it.state },
                png = Pngs.of(picture, maxWidth = PNG_WIDTH),
                width = picture.width,
                height = picture.height,
                fingerprint = Fingerprints.of(picture),
                blank = Fingerprints.isBlank(picture),
                settled = settled.settled,
            )
        captures[capture.id] = capture
        while (captures.size > max) captures.remove(captures.keys.first())
        return capture
    }

    @Synchronized
    fun get(id: String): Capture? = captures[id]

    @Synchronized
    fun all(): List<Capture> = captures.values.toList()

    companion object {
        const val DEFAULT_MAX: Int = 600
        const val PNG_WIDTH: Int = 720
    }
}
