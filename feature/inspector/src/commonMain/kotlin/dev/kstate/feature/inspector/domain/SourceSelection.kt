package dev.kstate.feature.inspector.domain

import dev.kstate.core.model.SourceView

/** Keeps [current] while it exists, else the first live source, else any source. */
fun selectSource(
    sources: List<SourceView>,
    current: String?,
): String? =
    sources.firstOrNull { it.key == current }?.key
        ?: sources.firstOrNull { it.live }?.key
        ?: sources.firstOrNull()?.key
