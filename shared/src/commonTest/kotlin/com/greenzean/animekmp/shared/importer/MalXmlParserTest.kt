package com.greenzean.animekmp.shared.importer

import com.greenzean.animekmp.shared.core.domain.Result
import com.greenzean.animekmp.shared.importer.domain.ImportError
import com.greenzean.animekmp.shared.importer.domain.MalExportEntry
import com.greenzean.animekmp.shared.importer.domain.MalExportFile
import com.greenzean.animekmp.shared.importer.domain.MalXmlParser
import com.greenzean.animekmp.shared.library.domain.WatchStatus
import okio.Buffer
import okio.GzipSink
import okio.buffer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

fun malAnimeXml(
    id: String? = "1",
    title: String = "<![CDATA[Cowboy Bebop]]>",
    episodes: String = "26",
    watched: String = "26",
    score: String = "9",
    status: String = "Completed",
    extra: String = "",
): String =
    buildString {
        append("  <anime>\n")
        if (id != null) append("    <series_animedb_id>$id</series_animedb_id>\n")
        append("    <series_title>$title</series_title>\n")
        append("    <series_episodes>$episodes</series_episodes>\n")
        append("    <my_watched_episodes>$watched</my_watched_episodes>\n")
        append("    <my_score>$score</my_score>\n")
        append("    <my_status>$status</my_status>\n")
        append(extra)
        append("  </anime>\n")
    }

fun malXml(vararg animes: String): String =
    "<?xml version=\"1.0\" encoding=\"UTF-8\" ?>\n<myanimelist>\n" +
        "  <myinfo>\n    <user_export_type>1</user_export_type>\n  </myinfo>\n" +
        animes.joinToString("") +
        "</myanimelist>\n"

fun gzip(bytes: ByteArray): ByteArray {
    val buffer = Buffer()
    val sink = GzipSink(buffer).buffer()
    sink.write(bytes)
    sink.close()
    return buffer.readByteArray()
}

class MalXmlParserTest {
    private val parser = MalXmlParser()

    private fun parse(xml: String): MalExportFile = (parser.parse(xml.encodeToByteArray()) as Result.Success).data

    @Test
    fun parsesPlainExport() {
        val file =
            parse(
                malXml(
                    malAnimeXml(
                        id = "1",
                        title = "<![CDATA[Cowboy Bebop]]>",
                        episodes = "26",
                        watched = "26",
                        score = "9",
                    ),
                    malAnimeXml(
                        id = "52991",
                        title = "Sousou no Frieren",
                        episodes = "28",
                        watched = "3",
                        score = "0",
                        status = "Watching",
                    ),
                ),
            )

        assertEquals(0, file.invalid)
        assertEquals(
            listOf(
                MalExportEntry(1, "Cowboy Bebop", 26, WatchStatus.COMPLETED, 26, 9),
                MalExportEntry(52991, "Sousou no Frieren", 28, WatchStatus.WATCHING, 3, null),
            ),
            file.entries,
        )
    }

    @Test
    fun parsesGzipExport() {
        val bytes = gzip(malXml(malAnimeXml(id = "1")).encodeToByteArray())

        val file = (parser.parse(bytes) as Result.Success).data

        assertEquals(1, file.entries.size)
        assertEquals("Cowboy Bebop", file.entries.single().title)
    }

    @Test
    fun cdataTitleKeepsSpecialCharacters() {
        val file = parse(malXml(malAnimeXml(title = "<![CDATA[Fate/Zero & <Friends> 'x']]>")))

        assertEquals("Fate/Zero & <Friends> 'x'", file.entries.single().title)
    }

    @Test
    fun parsesTextStatuses() {
        val file =
            parse(
                malXml(
                    malAnimeXml(id = "1", status = "Watching"),
                    malAnimeXml(id = "2", status = "Completed"),
                    malAnimeXml(id = "3", status = "On-Hold"),
                    malAnimeXml(id = "4", status = "Dropped"),
                    malAnimeXml(id = "5", status = "Plan to Watch"),
                ),
            )

        assertEquals(
            listOf(
                WatchStatus.WATCHING,
                WatchStatus.COMPLETED,
                WatchStatus.ON_HOLD,
                WatchStatus.DROPPED,
                WatchStatus.PLAN_TO_WATCH,
            ),
            file.entries.map { it.status },
        )
    }

    @Test
    fun parsesNumericStatusCodes() {
        val file =
            parse(
                malXml(
                    malAnimeXml(id = "1", status = "1"),
                    malAnimeXml(id = "2", status = "2"),
                    malAnimeXml(id = "3", status = "3"),
                    malAnimeXml(id = "4", status = "4"),
                    malAnimeXml(id = "6", status = "6"),
                ),
            )

        assertEquals(
            listOf(
                WatchStatus.WATCHING,
                WatchStatus.COMPLETED,
                WatchStatus.ON_HOLD,
                WatchStatus.DROPPED,
                WatchStatus.PLAN_TO_WATCH,
            ),
            file.entries.map { it.status },
        )
    }

    @Test
    fun zeroEpisodesBecomeNull() {
        assertEquals(null, parse(malXml(malAnimeXml(episodes = "0"))).entries.single().episodes)
    }

    @Test
    fun zeroScoreBecomesNull() {
        assertEquals(null, parse(malXml(malAnimeXml(score = "0"))).entries.single().score)
    }

    @Test
    fun unknownStatusIsInvalidAndSkipped() {
        val file = parse(malXml(malAnimeXml(id = "1", status = "Bingeing"), malAnimeXml(id = "2")))

        assertEquals(1, file.invalid)
        assertEquals(listOf(2), file.entries.map { it.malId })
    }

    @Test
    fun missingIdIsInvalidAndSkipped() {
        val file = parse(malXml(malAnimeXml(id = null), malAnimeXml(id = "2")))

        assertEquals(1, file.invalid)
        assertEquals(listOf(2), file.entries.map { it.malId })
    }

    @Test
    fun nonNumericIdIsInvalidAndSkipped() {
        val file = parse(malXml(malAnimeXml(id = "abc"), malAnimeXml(id = "2")))

        assertEquals(1, file.invalid)
        assertEquals(listOf(2), file.entries.map { it.malId })
    }

    @Test
    fun blankTitleIsInvalidAndSkipped() {
        val file = parse(malXml(malAnimeXml(id = "1", title = "<![CDATA[   ]]>"), malAnimeXml(id = "2")))

        assertEquals(1, file.invalid)
        assertEquals(listOf(2), file.entries.map { it.malId })
    }

    @Test
    fun repeatedIdKeepsTheFirstEntryAndCountsTheRepeatAsInvalid() {
        val file =
            parse(
                malXml(
                    malAnimeXml(id = "1", status = "Completed", score = "9"),
                    malAnimeXml(id = "2"),
                    malAnimeXml(id = "1", status = "Dropped", score = "3"),
                ),
            )

        assertEquals(1, file.invalid)
        assertEquals(listOf(1, 2), file.entries.map { it.malId })
        assertEquals(WatchStatus.COMPLETED, file.entries.first().status)
        assertEquals(9, file.entries.first().score)
    }

    @Test
    fun anInvalidFirstCopyDoesNotBlockALaterValidOneWithTheSameId() {
        val file = parse(malXml(malAnimeXml(id = "1", status = "Bingeing"), malAnimeXml(id = "1")))

        assertEquals(1, file.invalid)
        assertEquals(listOf(1), file.entries.map { it.malId })
    }

    @Test
    fun parsesTheStructureOfARealMalExport() {
        val file = parse(REAL_EXPORT_SAMPLE)

        assertEquals(0, file.invalid)
        assertEquals(listOf(48, 1292, 857), file.entries.map { it.malId })
        assertEquals(listOf(".hack//Sign", "Afro Samurai", "Air Gear"), file.entries.map { it.title })
        assertEquals(
            listOf(WatchStatus.DROPPED, WatchStatus.COMPLETED, WatchStatus.PLAN_TO_WATCH),
            file.entries.map { it.status },
        )
        assertEquals(listOf(3, 5, 0), file.entries.map { it.progress })
        assertEquals(listOf(6, 9, null), file.entries.map { it.score })
        assertEquals(listOf(26, 5, null), file.entries.map { it.episodes })
    }

    @Test
    fun parsesTheStructureOfARealMalExportWhenGzipped() {
        val result = parser.parse(gzip(REAL_EXPORT_SAMPLE.encodeToByteArray()))

        assertEquals(3, (result as Result.Success).data.entries.size)
    }

    @Test
    fun plainFileOverTheSizeLimitIsRejected() {
        val result = parser.parse(REAL_EXPORT_SAMPLE.encodeToByteArray(), maxXmlBytes = 100)

        assertEquals(Result.Error(ImportError.NotMalExport), result)
    }

    @Test
    fun gzipThatExpandsPastTheSizeLimitIsRejected() {
        val bytes = gzip(REAL_EXPORT_SAMPLE.encodeToByteArray())

        val result = parser.parse(bytes, maxXmlBytes = 100)

        assertEquals(Result.Error(ImportError.NotMalExport), result)
    }

    @Test
    fun fileExactlyAtTheSizeLimitIsAccepted() {
        val bytes = REAL_EXPORT_SAMPLE.encodeToByteArray()

        val result = parser.parse(bytes, maxXmlBytes = bytes.size.toLong())

        assertEquals(3, (result as Result.Success).data.entries.size)
    }

    @Test
    fun unknownElementsAreIgnored() {
        val file =
            parse(
                malXml(
                    malAnimeXml(
                        extra = "    <my_tags><![CDATA[a,b]]></my_tags>\n    <something_new>42</something_new>\n",
                    ),
                ),
            )

        assertEquals(0, file.invalid)
        assertEquals(1, file.entries.size)
    }

    @Test
    fun emptyMalExportHasNoEntries() {
        val file = parse(malXml())

        assertTrue(file.entries.isEmpty())
        assertEquals(0, file.invalid)
    }

    @Test
    fun wrongRootIsNotMalExport() {
        val result = parser.parse("<?xml version=\"1.0\"?><feed><anime/></feed>".encodeToByteArray())

        assertEquals(Result.Error(ImportError.NotMalExport), result)
    }

    @Test
    fun garbageBytesReturnError() {
        val result = parser.parse(byteArrayOf(0, 1, 2, 3, 120, 99, 42, -1, -2))

        assertIs<Result.Error<ImportError>>(result)
    }
}
