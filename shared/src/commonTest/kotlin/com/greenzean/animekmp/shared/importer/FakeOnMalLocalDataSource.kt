package com.greenzean.animekmp.shared.importer

import com.greenzean.animekmp.shared.importer.domain.OnMalLocalDataSource

class FakeOnMalLocalDataSource : OnMalLocalDataSource {
    val marked = mutableListOf<Int>()

    override suspend fun markOnMal(malId: Int) {
        marked += malId
    }
}
