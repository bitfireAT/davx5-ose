/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.davdroid.webdav

import at.bitfire.davdroid.webdav.cache.Cache
import at.bitfire.davdroid.webdav.cache.SegmentedCache
import org.apache.commons.io.FileUtils
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class SegmentedCacheTest {

    companion object {
        const val PAGE_SIZE = 100*FileUtils.ONE_KB.toInt()

        const val SAMPLE_KEY1 = "key1"
        const val PAGE2_SIZE = 123
    }

    val noCache = object: Cache<SegmentedCache.SegmentKey<String>> {
        override fun get(key: SegmentedCache.SegmentKey<String>) = null
        override fun getOrPut(key: SegmentedCache.SegmentKey<String>, generate: () -> ByteArray) = generate()
    }

    @Test
    fun testRead_AcrossPages() {
        val cache = SegmentedCache<String>(PAGE_SIZE, object: SegmentedCache.PageLoader<String> {
            override fun load(key: SegmentedCache.SegmentKey<String>, segmentSize: Int) =
                when (key.segment) {
                    0 -> ByteArray(PAGE_SIZE) { 1 }
                    1 -> ByteArray(PAGE2_SIZE) { 2 }
                    else -> throw IndexOutOfBoundsException()
                }
        }, noCache)
        val dst = ByteArray(20)
        assertEquals(20, cache.read(SAMPLE_KEY1, (PAGE_SIZE - 10).toLong(), dst.size, dst))
        assertArrayEquals(ByteArray(20) { i ->
            if (i < 10)
                1
            else
                2
        }, dst)
    }

    @Test
    fun testRead_AcrossPagesAndEOF() {
        val cache = SegmentedCache<String>(PAGE_SIZE, object: SegmentedCache.PageLoader<String> {
            override fun load(key: SegmentedCache.SegmentKey<String>, segmentSize: Int) =
                when (key.segment) {
                    0 -> ByteArray(PAGE_SIZE) { 1 }
                    1 -> ByteArray(PAGE2_SIZE) { 2 }
                    else -> throw IndexOutOfBoundsException()
                }
        }, noCache)
        val dst = ByteArray(10 + PAGE2_SIZE + 10)
        assertEquals(10 + PAGE2_SIZE, cache.read(SAMPLE_KEY1, (PAGE_SIZE - 10).toLong(), dst.size, dst))
        assertArrayEquals(ByteArray(10 + PAGE2_SIZE) { i ->
            if (i < 10)
                1
            else
                2
        }, dst.copyOf(10 + PAGE2_SIZE))
    }

    @Test
    fun testRead_ExactlyPageSize_BufferAlsoPageSize() {
        var loadCalled = 0
        val cache = SegmentedCache<String>(PAGE_SIZE, object: SegmentedCache.PageLoader<String> {
            override fun load(key: SegmentedCache.SegmentKey<String>, segmentSize: Int): ByteArray {
                loadCalled++
                if (key.segment == 0)
                    return ByteArray(PAGE_SIZE)
                else
                    throw IndexOutOfBoundsException()
            }
        }, noCache)
        val dst = ByteArray(PAGE_SIZE)
        assertEquals(PAGE_SIZE, cache.read(SAMPLE_KEY1, 0, dst.size, dst))
        assertEquals(1, loadCalled)
    }

    @Test
    fun testRead_ExactlyPageSize_ButLargerBuffer() {
        var loadCalled = 0
        val cache = SegmentedCache<String>(PAGE_SIZE, object: SegmentedCache.PageLoader<String> {
            override fun load(key: SegmentedCache.SegmentKey<String>, segmentSize: Int): ByteArray {
                loadCalled++
                if (key.segment == 0)
                    return ByteArray(PAGE_SIZE)
                else
                    throw IndexOutOfBoundsException()
            }
        }, noCache)
        val dst = ByteArray(PAGE_SIZE + 10)     // 10 bytes more so that the second segment is read
        assertEquals(PAGE_SIZE, cache.read(SAMPLE_KEY1, 0, dst.size, dst))
        assertEquals(2, loadCalled)
    }

    @Test
    fun testRead_Offset() {
        val cache = SegmentedCache<String>(PAGE_SIZE, object: SegmentedCache.PageLoader<String> {
            override fun load(key: SegmentedCache.SegmentKey<String>, segmentSize: Int): ByteArray {
                if (key.segment == 0)
                    return ByteArray(PAGE_SIZE) { 1 }
                else
                    throw IndexOutOfBoundsException()
            }
        }, noCache)
        val dst = ByteArray(PAGE_SIZE)
        assertEquals(PAGE_SIZE - 100, cache.read(SAMPLE_KEY1, 100, dst.size, dst))
        assertArrayEquals(ByteArray(PAGE_SIZE) { i ->
            if (i < PAGE_SIZE - 100)
                1
            else
                0
        }, dst)
    }

    @Test
    fun testRead_OnlyOnePageSmallerThanPageSize_From0() {
        val contentSize = 123
        val cache = SegmentedCache<String>(PAGE_SIZE, object: SegmentedCache.PageLoader<String> {
            override fun load(key: SegmentedCache.SegmentKey<String>, segmentSize: Int) =
                when (key.segment) {
                    0 -> ByteArray(contentSize) { it.toByte() }
                    else -> throw IndexOutOfBoundsException()
                }
        }, noCache)

        // read less than content size
        var dst = ByteArray(10)     // 10 < contentSize
        assertEquals(10, cache.read(SAMPLE_KEY1, 0, dst.size, dst))
        assertArrayEquals(ByteArray(10) { it.toByte() }, dst)

        // read more than content size
        dst = ByteArray(1000)       // 1000 > contentSize
        assertEquals(contentSize, cache.read(SAMPLE_KEY1, 0, dst.size, dst))
        assertArrayEquals(ByteArray(1000) { i ->
            if (i < contentSize)
                i.toByte()
            else
                0
        }, dst)
    }

    @Test
    fun testRead_ZeroByteFile() {
        val cache = SegmentedCache<String>(PAGE_SIZE, object: SegmentedCache.PageLoader<String> {
            override fun load(key: SegmentedCache.SegmentKey<String>, segmentSize: Int) =
                throw IndexOutOfBoundsException()
        }, noCache)
        val dst = ByteArray(10)
        assertEquals(0, cache.read(SAMPLE_KEY1, 10, dst.size, dst))
    }

}