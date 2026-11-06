package dev.kstate.host.collab

/** The web viewer's files by relative path, such as `index.html`; null when there is no such file. */
fun interface ViewerAssets {
    fun read(path: String): ByteArray?

    companion object {
        /** The bundle the build copies under `viewer/` on the classpath. Only plain relative names resolve. */
        fun fromClasspath(loader: ClassLoader = ViewerAssets::class.java.classLoader): ViewerAssets =
            ViewerAssets { path ->
                if (!SAFE_PATH.matches(path) ||
                    ".." in path
                ) {
                    null
                } else {
                    loader.getResourceAsStream("viewer/$path")?.use { it.readBytes() }
                }
            }

        private val SAFE_PATH = Regex("[A-Za-z0-9_-][A-Za-z0-9._/-]*")

        fun contentType(path: String): String =
            when (path.substringAfterLast('.', "")) {
                "html" -> "text/html; charset=utf-8"
                "js", "mjs" -> "text/javascript; charset=utf-8"
                "wasm" -> "application/wasm"
                "json", "map" -> "application/json"
                "css" -> "text/css; charset=utf-8"
                "png" -> "image/png"
                "svg" -> "image/svg+xml"
                "ttf" -> "font/ttf"
                "txt" -> "text/plain; charset=utf-8"
                else -> "application/octet-stream"
            }
    }
}
