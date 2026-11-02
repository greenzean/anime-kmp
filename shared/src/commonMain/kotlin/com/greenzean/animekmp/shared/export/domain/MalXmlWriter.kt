package com.greenzean.animekmp.shared.export.domain

import com.greenzean.animekmp.shared.library.domain.LibraryEntry
import com.greenzean.animekmp.shared.library.domain.WatchStatus

class MalXmlWriter {
    fun write(entries: List<LibraryEntry>): String =
        buildString {
            append("<?xml version=\"1.0\" encoding=\"UTF-8\" ?>\n")
            append("<myanimelist>\n")
            append("  <myinfo>\n")
            append("    <user_id></user_id>\n")
            append("    <user_name></user_name>\n")
            append("    <user_export_type>1</user_export_type>\n")
            append("    <user_total_anime>${entries.size}</user_total_anime>\n")
            append("    <user_total_watching>${count(entries, WatchStatus.WATCHING)}</user_total_watching>\n")
            append("    <user_total_completed>${count(entries, WatchStatus.COMPLETED)}</user_total_completed>\n")
            append("    <user_total_onhold>${count(entries, WatchStatus.ON_HOLD)}</user_total_onhold>\n")
            append("    <user_total_dropped>${count(entries, WatchStatus.DROPPED)}</user_total_dropped>\n")
            val planned = count(entries, WatchStatus.PLAN_TO_WATCH)
            append("    <user_total_plantowatch>$planned</user_total_plantowatch>\n")
            append("  </myinfo>\n")
            entries.forEach { appendEntry(it) }
            append("</myanimelist>\n")
        }

    private fun count(
        entries: List<LibraryEntry>,
        status: WatchStatus,
    ): Int = entries.count { it.status == status }

    private fun StringBuilder.appendEntry(entry: LibraryEntry) {
        append("  <anime>\n")
        line("series_animedb_id", entry.anime.malId.toString())
        line("series_title", cdata(entry.anime.title))
        line("series_type", "")
        line("series_episodes", (entry.anime.episodes ?: 0).toString())
        line("my_id", "0")
        line("my_watched_episodes", entry.progress.toString())
        line("my_start_date", "0000-00-00")
        line("my_finish_date", "0000-00-00")
        line("my_fansub_group", cdata(""))
        line("my_rated", "")
        line("my_score", (entry.score ?: 0).toString())
        line("my_dvd", "0")
        line("my_storage", "")
        line("my_status", statusText(entry.status))
        line("my_comments", cdata(""))
        line("my_times_watched", "0")
        line("my_rewatch_value", "")
        line("my_downloaded_eps", "0")
        line("my_tags", cdata(""))
        line("my_rewatching", "0")
        line("my_rewatching_ep", "0")
        line("update_on_import", "1")
        append("  </anime>\n")
    }

    private fun StringBuilder.line(
        name: String,
        value: String,
    ) {
        append("    <")
            .append(name)
            .append('>')
            .append(value)
            .append("</")
            .append(name)
            .append(">\n")
    }

    private fun cdata(text: String): String = "<![CDATA[" + text.replace("]]>", "]]]]><![CDATA[>") + "]]>"

    private fun statusText(status: WatchStatus): String =
        when (status) {
            WatchStatus.WATCHING -> "Watching"
            WatchStatus.COMPLETED -> "Completed"
            WatchStatus.ON_HOLD -> "On-Hold"
            WatchStatus.DROPPED -> "Dropped"
            WatchStatus.PLAN_TO_WATCH -> "Plan to Watch"
        }
}
