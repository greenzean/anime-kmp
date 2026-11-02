package com.greenzean.animekmp.composeapp.foryou

object ForYouTags {
    const val RETRY = "for_you_retry"
    const val LIST = "for_you_list"
    const val LOADING = "for_you_loading"

    fun item(malId: Int) = "for_you_item_$malId"

    fun add(malId: Int) = "for_you_add_$malId"
}
