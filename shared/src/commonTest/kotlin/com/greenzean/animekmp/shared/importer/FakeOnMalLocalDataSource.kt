package com.greenzean.animekmp.shared.importer

import com.greenzean.animekmp.shared.importer.domain.OnMalLocalDataSource

class FakeOnMalLocalDataSource : OnMalLocalDataSource {
    val marked = mutableListOf<Int>()
    var active: List<Int> = emptyList()

    override suspend fun markOnMal(malId: Int) {
        marked += malId
    }

    override suspend fun activeOnMalIds(): List<Int> = active
}
