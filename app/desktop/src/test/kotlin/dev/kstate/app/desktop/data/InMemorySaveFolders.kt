package dev.kstate.app.desktop.data

import java.nio.file.Path

class InMemorySaveFolders : SaveFolders {
    override var last: Path? = null
}
