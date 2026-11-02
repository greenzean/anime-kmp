package com.greenzean.animekmp.composeapp.conflicts

object ConflictsTags {
    const val LIST = "conflicts_list"
    const val EMPTY = "conflicts_empty"
    const val KEEP_ALL_APP = "conflicts_keep_all_app"
    const val KEEP_ALL_MAL = "conflicts_keep_all_mal"
    const val KEEP_ALL_MAL_CONFIRM = "conflicts_keep_all_mal_confirm"
    const val LIBRARY_BANNER = "library_conflicts_banner"
    const val SETTINGS_ROW = "settings_conflicts_row"

    fun item(malId: Int) = "conflict_item_$malId"

    fun keepApp(malId: Int) = "conflict_keep_app_$malId"

    fun keepMal(malId: Int) = "conflict_keep_mal_$malId"
}
