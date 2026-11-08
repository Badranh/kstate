@file:OptIn(ExperimentalWasmJsInterop::class)

package dev.kstate.app.web.data.video

import org.khronos.webgl.Int8Array

/** A browser `VideoDecoder` whose pictures come back as RGBA, through a 2D canvas. */
external interface JsDecoder : JsAny

@Suppress("UNUSED_PARAMETER")
internal fun createDecoder(
    onFrame: (width: Int, height: Int, rgba: Int8Array, timestamp: Double) -> Unit,
    onError: (message: String) -> Unit,
): JsDecoder =
    js(
        """{
        let canvas = null, ctx = null;
        const decoder = new VideoDecoder({
            output: (frame) => {
                const w = frame.displayWidth, h = frame.displayHeight;
                if (!canvas || canvas.width !== w || canvas.height !== h) {
                    canvas = new OffscreenCanvas(w, h);
                    ctx = canvas.getContext('2d', { willReadFrequently: true });
                }
                ctx.drawImage(frame, 0, 0);
                const ts = frame.timestamp;
                frame.close();
                const pixels = ctx.getImageData(0, 0, w, h).data;
                onFrame(w, h, new Int8Array(pixels.buffer, pixels.byteOffset, pixels.byteLength), ts);
            },
            error: (e) => onError(String(e)),
        });
        return decoder;
    }""",
    )

/** Annex-B H.264 (no `description`), tuned for latency over smoothness. Null when it took, else why not. */
@Suppress("UNUSED_PARAMETER")
internal fun configureDecoder(
    decoder: JsDecoder,
    codec: String,
): String? =
    js(
        """{
        try {
            decoder.configure({ codec: codec, optimizeForLatency: true });
            return null;
        } catch (e) {
            return String(e);
        }
    }""",
    )

/** Null when the chunk was queued; otherwise why not (a closed decoder throws here). */
@Suppress("UNUSED_PARAMETER")
internal fun decodeChunk(
    decoder: JsDecoder,
    data: Int8Array,
    timestamp: Double,
    keyFrame: Boolean,
): String? =
    js(
        """{
        try {
            const type = keyFrame ? 'key' : 'delta';
            decoder.decode(new EncodedVideoChunk({ type: type, timestamp: timestamp, data: data }));
            return null;
        } catch (e) {
            return String(e);
        }
    }""",
    )

@Suppress("UNUSED_PARAMETER")
internal fun closeDecoder(decoder: JsDecoder): Unit = js("{ if (decoder.state !== 'closed') decoder.close(); }")

/** False outside a secure context, as on plain http to a LAN address: WebCodecs needs https or localhost. */
internal fun webCodecsAvailable(): Boolean = js("typeof VideoDecoder !== 'undefined'")
