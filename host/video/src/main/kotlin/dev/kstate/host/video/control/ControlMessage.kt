package dev.kstate.host.video.control

/** A point in video-frame pixels plus the frame size it refers to; the server drops stale sizes. */
data class Position(
    val x: Int,
    val y: Int,
    val screenWidth: Int,
    val screenHeight: Int,
)

/** Messages sent on the scrcpy control socket (the subset Kstate injects). */
sealed interface ControlMessage {
    data class InjectKeycode(
        val action: Int,
        val keycode: Int,
        val repeat: Int,
        val metaState: Int,
    ) : ControlMessage

    data class InjectText(
        val text: String,
    ) : ControlMessage

    /** Puts [text] on the device's clipboard and, with [paste], presses Paste in the focused field. */
    data class SetClipboard(
        val text: String,
        val paste: Boolean = true,
    ) : ControlMessage

    data class InjectTouch(
        val action: Int,
        val pointerId: Long,
        val position: Position,
        val pressure: Float,
        val actionButton: Int,
        val buttons: Int,
    ) : ControlMessage

    data class InjectScroll(
        val position: Position,
        /** In [-16, 16], as scrcpy defines it. */
        val horizontal: Float,
        val vertical: Float,
        val buttons: Int,
    ) : ControlMessage

    companion object {
        /** Android MotionEvent / KeyEvent actions. */
        const val ACTION_DOWN: Int = 0
        const val ACTION_UP: Int = 1
        const val ACTION_MOVE: Int = 2

        /** Makes Android treat the pointer as a finger, not a mouse. */
        const val POINTER_ID_GENERIC_FINGER: Long = -2

        /** The server's INJECT_TEXT_MAX_LENGTH, in UTF-8 bytes. */
        const val MAX_TEXT_BYTES: Int = 300

        /** The server's CLIPBOARD_TEXT_MAX_LENGTH, in UTF-8 bytes. */
        const val MAX_CLIPBOARD_BYTES: Int = 262_130
    }
}
