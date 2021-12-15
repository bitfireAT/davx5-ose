/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.davdroid.webdav

import at.bitfire.davdroid.webdav.cache.MemoryCache
import org.apache.commons.io.FileUtils
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class MemoryCacheTest {

    companion object {
        val SAMPLE_KEY1 = "key1"
        val SAMPLE_CONTENT1 = "Sample Content 1".toByteArray()
        val SAMPLE_CONTENT2 = "Another Content".toByteArray()
    }

    lateinit var storage: MemoryCache<String>


    @Before
    fun createStorage() {
        storage = MemoryCache(1*FileUtils.ONE_MB.toInt())
    }


    @Test
    fun testGet() {
        // no entry yet, get should return null
        assertNull(storage.get(SAMPLE_KEY1))

        // add entry
        storage.getOrPut(SAMPLE_KEY1) { SAMPLE_CONTENT1 }
        assertArrayEquals(SAMPLE_CONTENT1, storage.get(SAMPLE_KEY1))
    }

    @Test
    fun testGetOrPut() {
        assertNull(storage.get(SAMPLE_KEY1))
        // no entry yet; SAMPLE_CONTENT1 should be generated
        var calledGenerateSampleContent1 = false
        assertArrayEquals(SAMPLE_CONTENT1, storage.getOrPut(SAMPLE_KEY1) {
            calledGenerateSampleContent1 = true
            SAMPLE_CONTENT1
        })
        assertTrue(calledGenerateSampleContent1)
        assertNotNull(storage.get(SAMPLE_KEY1))

        // now there's a SAMPLE_CONTENT1 entry, it should be returned while SAMPLE_CONTENT2 is not generated
        var calledGenerateSampleContent2 = false
        assertArrayEquals(SAMPLE_CONTENT1, storage.getOrPut(SAMPLE_KEY1) {
            calledGenerateSampleContent2 = true
            SAMPLE_CONTENT2
        })
        assertFalse(calledGenerateSampleContent2)
    }

    @Test
    fun testMaxCacheSize() {
        // Cache size is 1 MB. Add 11*100 kB -> the first entry should be gone then
        for (i in 0 until 11) {
            val key = "key$i"
            storage.getOrPut(key) {
                ByteArray(100 * FileUtils.ONE_KB.toInt()) { i.toByte() }
            }
            assertNotNull(storage.get(key))
        }

        // now key0 should have been evicted and only key1..key11 should be there
        assertNull(storage.get("key0"))
        for (i in 1 until 11)
            assertNotNull(storage.get("key$i"))
    }

}