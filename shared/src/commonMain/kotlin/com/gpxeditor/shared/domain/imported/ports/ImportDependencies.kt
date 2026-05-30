package com.gpxeditor.shared.domain.imported.ports

interface ImportIdGenerator {
    fun nextId(): String
}

interface ImportClock {
    fun nowIsoString(): String
}
