package com.greenzean.animekmp.shared.export

import com.greenzean.animekmp.shared.export.domain.MalXmlWriter
import com.greenzean.animekmp.shared.library.domain.Anime
import com.greenzean.animekmp.shared.library.domain.LibraryEntry
import com.greenzean.animekmp.shared.library.domain.SyncState
import com.greenzean.animekmp.shared.library.domain.WatchStatus
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Instant

fun exportEntry(
    malId: Int = 1,
    title: String = "Cowboy Bebop",
    episodes: Int? = 26,
    status: WatchStatus = WatchStatus.WATCHING,
    progress: Int = 0,
    score: Int? = null,
) = LibraryEntry(
    id = "entry-$malId",
    anime = Anime(malId, title, episodes = episodes),
    status = status,
    progress = progress,
    score = score,
    updatedAt = Instant.fromEpochMilliseconds(0),
    syncState = SyncState.SYNCED,
)

class MalXmlWriterTest {
    private val writer = MalXmlWriter()

    private fun assertContains(
        xml: String,
        part: String,
    ) = assertTrue(xml.contains(part), "Missing <$part> in:\n$xml")

    @Test
    fun writesHeaderAndRoot() {
        val xml = writer.write(listOf(exportEntry()))

        assertTrue(xml.trimStart().startsWith("<?xml version=\"1.0\" encoding=\"UTF-8\" ?>"))
        assertContains(xml, "<myanimelist>")
        assertContains(xml, "</myanimelist>")
        assertContains(xml, "<myinfo>")
        assertContains(xml, "<user_export_type>1</user_export_type>")
    }

    @Test
    fun writesCountsPerStatus() {
        val xml =
            writer.write(
                listOf(
                    exportEntry(1, status = WatchStatus.WATCHING),
                    exportEntry(2, status = WatchStatus.COMPLETED),
                    exportEntry(3, status = WatchStatus.COMPLETED),
                    exportEntry(4, status = WatchStatus.ON_HOLD),
                    exportEntry(5, status = WatchStatus.DROPPED),
                    exportEntry(6, status = WatchStatus.PLAN_TO_WATCH),
                    exportEntry(7, status = WatchStatus.PLAN_TO_WATCH),
                    exportEntry(8, status = WatchStatus.PLAN_TO_WATCH),
                ),
            )

        assertContains(xml, "<user_total_anime>8</user_total_anime>")
        assertContains(xml, "<user_total_watching>1</user_total_watching>")
        assertContains(xml, "<user_total_completed>2</user_total_completed>")
        assertContains(xml, "<user_total_onhold>1</user_total_onhold>")
        assertContains(xml, "<user_total_dropped>1</user_total_dropped>")
        assertContains(xml, "<user_total_plantowatch>3</user_total_plantowatch>")
    }

    @Test
    fun writesEveryStatusString() {
        val expected =
            mapOf(
                WatchStatus.WATCHING to "Watching",
                WatchStatus.COMPLETED to "Completed",
                WatchStatus.ON_HOLD to "On-Hold",
                WatchStatus.DROPPED to "Dropped",
                WatchStatus.PLAN_TO_WATCH to "Plan to Watch",
            )

        expected.forEach { (status, text) ->
            assertContains(writer.write(listOf(exportEntry(status = status))), "<my_status>$text</my_status>")
        }
    }

    @Test
    fun writesEntryFields() {
        val xml =
            writer.write(
                listOf(
                    exportEntry(
                        52991,
                        "Sousou no Frieren",
                        episodes = 28,
                        status = WatchStatus.COMPLETED,
                        progress = 28,
                        score = 10,
                    ),
                ),
            )

        assertContains(xml, "<anime>")
        assertContains(xml, "<series_animedb_id>52991</series_animedb_id>")
        assertContains(xml, "<series_title><![CDATA[Sousou no Frieren]]></series_title>")
        assertContains(xml, "<series_episodes>28</series_episodes>")
        assertContains(xml, "<my_watched_episodes>28</my_watched_episodes>")
        assertContains(xml, "<my_score>10</my_score>")
        assertContains(xml, "<update_on_import>1</update_on_import>")
    }

    @Test
    fun nullScoreAndEpisodesAreWrittenAsZero() {
        val xml = writer.write(listOf(exportEntry(episodes = null, score = null)))

        assertContains(xml, "<series_episodes>0</series_episodes>")
        assertContains(xml, "<my_score>0</my_score>")
    }

    @Test
    fun titleWithMarkupIsKeptInsideCdata() {
        val xml = writer.write(listOf(exportEntry(title = "Tom & Jerry <3 \"x\"")))

        assertContains(xml, "<series_title><![CDATA[Tom & Jerry <3 \"x\"]]></series_title>")
    }

    @Test
    fun cdataTerminatorInTitleIsSplit() {
        val xml = writer.write(listOf(exportEntry(title = "A]]>B")))

        assertContains(xml, "<![CDATA[A]]]]><![CDATA[>B]]>")
    }

    @Test
    fun writesOneAnimeBlockPerEntry() {
        val xml = writer.write(listOf(exportEntry(1), exportEntry(2), exportEntry(3)))

        assertTrue(xml.split("<anime>").size - 1 == 3)
    }
}
