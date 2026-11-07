package dev.kstate.host.desktop

import java.nio.file.Path

class InMemorySaveFolders : SaveFolders {
    override var last: Path? = null
}
