package com.greenzean.animekmp.shared.importer.domain

import com.greenzean.animekmp.shared.core.domain.Result
import com.greenzean.animekmp.shared.library.domain.WatchStatus
import nl.adaptivity.xmlutil.EventType
import nl.adaptivity.xmlutil.XmlReader
import nl.adaptivity.xmlutil.xmlStreaming
import okio.Buffer
import okio.GzipSource
import okio.buffer

class MalXmlParser {
    fun parse(
        bytes: ByteArray,
        maxXmlBytes: Long = MAX_IMPORT_XML_BYTES,
    ): Result<MalExportFile, ImportError> =
        try {
            readFile(xmlStreaming.newReader(unzipIfNeeded(bytes, maxXmlBytes).decodeToString()))
        } catch (_: Exception) {
            Result.Error(ImportError.NotMalExport)
        }

    private fun unzipIfNeeded(
        bytes: ByteArray,
        maxXmlBytes: Long,
    ): ByteArray {
        val gzipped = bytes.size >= 2 && bytes[0] == GZIP_MAGIC_1 && bytes[1] == GZIP_MAGIC_2
        val xml =
            if (gzipped) {
                GzipSource(Buffer().write(bytes)).buffer().use { it.readBytesAtMost(maxXmlBytes) }
            } else {
                bytes.takeIf { it.size <= maxXmlBytes }
            }
        return requireNotNull(xml) { "Export is larger than $maxXmlBytes bytes" }
    }

    private fun readFile(reader: XmlReader): Result<MalExportFile, ImportError> {
        if (!reader.atRoot()) return Result.Error(ImportError.NotMalExport)
        val entries = mutableListOf<MalExportEntry>()
        var invalid = 0
        while (true) {
            val event = reader.next()
            if (event == EventType.END_DOCUMENT) break
            if (event == EventType.START_ELEMENT && reader.localName == ANIME) {
                val entry = readFields(reader).toEntry()
                if (entry == null || entries.any { it.malId == entry.malId }) invalid++ else entries += entry
            }
        }
        return Result.Success(MalExportFile(entries, invalid))
    }

    private fun XmlReader.atRoot(): Boolean {
        var event = next()
        while (event != EventType.START_ELEMENT && event != EventType.END_DOCUMENT) event = next()
        return event == EventType.START_ELEMENT && localName == ROOT
    }

    private fun readFields(reader: XmlReader): Map<String, String> {
        val fields = mutableMapOf<String, String>()
        val buffer = StringBuilder()
        var current: String? = null
        var depth = 1
        while (depth > 0) {
            when (reader.next()) {
                EventType.START_ELEMENT -> {
                    depth++
                    if (depth == 2) {
                        current = reader.localName
                        buffer.clear()
                    }
                }

                EventType.TEXT, EventType.CDSECT, EventType.ENTITY_REF -> {
                    if (depth == 2) buffer.append(reader.text)
                }

                EventType.END_ELEMENT -> {
                    if (depth == 2) {
                        current?.let { fields[it] = buffer.toString().trim() }
                        current = null
                    }
                    depth--
                }

                EventType.END_DOCUMENT -> {
                    error("Unexpected end of document")
                }

                else -> {
                    Unit
                }
            }
        }
        return fields
    }

    private fun Map<String, String>.toEntry(): MalExportEntry? =
        try {
            val episodes = optionalInt("series_episodes", 0..Int.MAX_VALUE)
            val score = optionalInt("my_score", 0..MAX_SCORE)
            MalExportEntry(
                malId = requiredInt("series_animedb_id", 1..Int.MAX_VALUE),
                title = requireNotNull(this["series_title"]?.takeIf { it.isNotBlank() }),
                episodes = episodes.takeIf { it > 0 },
                status = requireNotNull(parseStatus(this["my_status"])),
                progress = requiredInt("my_watched_episodes", 0..Int.MAX_VALUE),
                score = score.takeIf { it > 0 },
            )
        } catch (_: IllegalArgumentException) {
            null
        }

    private fun Map<String, String>.requiredInt(
        name: String,
        range: IntRange,
    ): Int = requireNotNull(this[name]?.toIntOrNull()?.takeIf { it in range })

    private fun Map<String, String>.optionalInt(
        name: String,
        range: IntRange,
    ): Int {
        val raw = this[name].orEmpty()
        return if (raw.isEmpty()) 0 else requiredInt(name, range)
    }

    private fun parseStatus(text: String?): WatchStatus? =
        when (text?.trim()?.lowercase()) {
            "watching", "1" -> WatchStatus.WATCHING
            "completed", "2" -> WatchStatus.COMPLETED
            "on-hold", "3" -> WatchStatus.ON_HOLD
            "dropped", "4" -> WatchStatus.DROPPED
            "plan to watch", "6" -> WatchStatus.PLAN_TO_WATCH
            else -> null
        }

    private companion object {
        const val ROOT = "myanimelist"
        const val ANIME = "anime"
        const val MAX_SCORE = 10
        const val GZIP_MAGIC_1: Byte = 0x1f
        const val GZIP_MAGIC_2: Byte = 0x8b.toByte()
    }
}
