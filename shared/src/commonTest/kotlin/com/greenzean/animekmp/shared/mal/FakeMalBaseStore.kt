package com.greenzean.animekmp.shared.mal

import com.greenzean.animekmp.shared.mal.domain.MalBaseStore
import com.greenzean.animekmp.shared.mal.domain.MalBaseValues

class FakeMalBaseStore : MalBaseStore {
    val values = mutableMapOf<Int, MalBaseValues>()
    val saved = mutableListOf<Pair<Int, MalBaseValues>>()
    val deleted = mutableListOf<Int>()
    var clears = 0

    override suspend fun get(malId: Int): MalBaseValues? = values[malId]

    override suspend fun save(
        malId: Int,
        values: MalBaseValues,
    ) {
        saved += malId to values
        this.values[malId] = values
    }

    override suspend fun delete(malId: Int) {
        deleted += malId
        values.remove(malId)
    }

    override suspend fun clear() {
        clears++
        values.clear()
    }
}
