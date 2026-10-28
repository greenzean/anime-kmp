package com.greenzean.animekmp.shared.library.domain

object EntryRules {
    const val MAX_PROGRESS = 10_000
    private const val MIN_SCORE = 1
    private const val MAX_SCORE = 10

    fun apply(
        entry: LibraryEntry,
        edit: EntryEdit,
    ): LibraryEntry {
        val total = entry.anime.episodes?.takeIf { it > 0 }
        return when (edit) {
            is EntryEdit.Status -> {
                val progress =
                    when {
                        edit.status == WatchStatus.COMPLETED && total != null -> total
                        edit.status == WatchStatus.PLAN_TO_WATCH -> 0
                        else -> entry.progress
                    }
                entry.copy(status = edit.status, progress = progress)
            }

            is EntryEdit.Progress -> {
                withProgress(entry, edit.progress, total)
            }

            EntryEdit.Increment -> {
                withProgress(entry, entry.progress + 1, total)
            }

            is EntryEdit.Score -> {
                if (edit.score == null || edit.score in MIN_SCORE..MAX_SCORE) entry.copy(score = edit.score) else entry
            }
        }
    }

    private fun withProgress(
        entry: LibraryEntry,
        requested: Int,
        total: Int?,
    ): LibraryEntry {
        val progress = requested.coerceIn(0, total ?: MAX_PROGRESS)
        val status =
            when {
                total != null && progress == total -> WatchStatus.COMPLETED
                progress > 0 && entry.status == WatchStatus.PLAN_TO_WATCH -> WatchStatus.WATCHING
                total != null && entry.status == WatchStatus.COMPLETED -> WatchStatus.WATCHING
                else -> entry.status
            }
        return entry.copy(progress = progress, status = status)
    }
}
