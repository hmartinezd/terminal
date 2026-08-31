package com.venkoi.terminal.integration.sales

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Serialization boundary for the canonical SalesExport V1 interchange document. */
object SalesExportJsonCodec {
    private val json = Json {
        prettyPrint = true
        prettyPrintIndent = "  "
        encodeDefaults = true
        explicitNulls = true
        ignoreUnknownKeys = false
    }

    fun encode(value: SalesExportV1): String = json.encodeToString(value)
}
