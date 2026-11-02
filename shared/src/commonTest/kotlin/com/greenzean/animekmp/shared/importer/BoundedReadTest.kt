package com.greenzean.animekmp.shared.importer

import com.greenzean.animekmp.shared.importer.domain.readBytesAtMost
import okio.Buffer
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertNull

class BoundedReadTest {
    @Test
    fun readsEverythingWhenTheSourceIsWithinTheLimit() {
        val source = Buffer().write(byteArrayOf(1, 2, 3))

        assertContentEquals(byteArrayOf(1, 2, 3), source.readBytesAtMost(3))
    }

    @Test
    fun returnsNullWhenTheSourceIsOverTheLimit() {
        val source = Buffer().write(byteArrayOf(1, 2, 3, 4))

        assertNull(source.readBytesAtMost(3))
    }

    @Test
    fun readsAnEmptySource() {
        assertContentEquals(ByteArray(0), Buffer().readBytesAtMost(3))
    }
}
