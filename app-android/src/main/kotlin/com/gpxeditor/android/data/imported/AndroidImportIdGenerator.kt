package com.gpxeditor.android.data.imported

import com.gpxeditor.shared.domain.imported.ports.ImportIdGenerator
import java.util.UUID

class AndroidImportIdGenerator : ImportIdGenerator {
    override fun nextId(): String = UUID.randomUUID().toString()
}
