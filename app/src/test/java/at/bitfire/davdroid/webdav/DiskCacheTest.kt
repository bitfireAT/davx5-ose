/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.davdroid.webdav

import at.bitfire.davdroid.webdav.cache.DiskCache
import org.apache.commons.io.FileUtils
import org.apache.commons.io.IOUtils
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DiskCacheTest {

    companion object {
        const val SOME_KEY = "key1"
        const val SOME_VALUE_LENGTH = 15
        val SOME_VALUE = ByteArray(SOME_VALUE_LENGTH) { it.toByte() }
        val SOME_OTHER_VALUE = ByteArray(30) { (it/2).toByte() }

        const val MAX_CACHE_MB = 10
        const val MAX_CACHE_SIZE = MAX_CACHE_MB*FileUtils.ONE_MB
    }

    @Rule
    @JvmField
    val tempDir = TemporaryFolder()

    lateinit var cache: DiskCache


    @Before
    fun createCache() {
        cache = DiskCache(tempDir.newFolder(), MAX_CACHE_SIZE)
    }

    @After
    fun deleteCache() {
        assertTrue(cache.cacheDir.deleteRecursively())
    }


    @Test
    fun testGet_Null() {
        assertNull(cache.get(SOME_KEY) { null })
        assertEquals(0, cache.entries())
    }

    @Test
    fun testGet_NotNull() {
        assertArrayEquals(SOME_VALUE, cache.get(SOME_KEY) { SOME_VALUE })

        // non-null value should have been written to cache
        assertEquals(1, cache.entries())
        assertArrayEquals(SOME_VALUE, cache.get(SOME_KEY) { SOME_OTHER_VALUE })
    }

    @Test
    fun testGet_NotNull_Partial() {
        assertArrayEquals(ByteArray(2) { (it+1).toByte() }, cache.get(SOME_KEY, 1, 2) { SOME_VALUE })

        // full non-null value should have been written to cache
        assertEquals(1, cache.entries())
        assertArrayEquals(SOME_VALUE, cache.get(SOME_KEY) { SOME_OTHER_VALUE })
    }

    @Test
    fun testGet_NotNull_Partial_LargerThanSize() {
        assertArrayEquals(ByteArray(SOME_VALUE_LENGTH - 1) { (it+1).toByte() }, cache.get(SOME_KEY, 1, SOME_VALUE_LENGTH*2) { SOME_VALUE })

        // full non-null value should have been written to cache
        assertEquals(1, cache.entries())
        assertArrayEquals(SOME_VALUE, cache.get(SOME_KEY) { SOME_OTHER_VALUE })
    }


    @Test
    fun testGetFile_Null() {
        assertNull(cache.getFile(SOME_KEY) { null })

        // null value shouldn't have been written to cache
        assertEquals(0, cache.entries())
        val file = cache.getFile(SOME_KEY) { SOME_VALUE }
        file!!.inputStream().use { input ->
            assertArrayEquals(SOME_VALUE, IOUtils.toByteArray(input))
        }
    }

    @Test
    fun testGetFile_NotNull() {
        cache.getFile(SOME_KEY) { SOME_VALUE }!!.inputStream().use { input ->
            assertArrayEquals(SOME_VALUE, IOUtils.toByteArray(input))
        }

        // non-null value should have been written to cache
        assertEquals(1, cache.entries())
        cache.getFile(SOME_KEY) { SOME_OTHER_VALUE }!!.inputStream().use { input ->
            assertArrayEquals(SOME_VALUE, IOUtils.toByteArray(input))
        }
    }


    @Test
    fun testClear() {
        for (i in 1..50) {
            cache.get(i.toString()) { i.toString().toByteArray() }
        }
        assertEquals(50, cache.entries())

        cache.clear()
        assertEquals(0, cache.entries())
    }


    @Test
    fun testTrim() {
        assertEquals(0, cache.entries())

        cache.get(SOME_KEY) { SOME_VALUE }
        assertEquals(1, cache.entries())

        cache.trim()
        assertEquals(1, cache.entries())

        // add 11 x 1 MB
        for (i in 0..MAX_CACHE_MB) {
            cache.get(i.toString()) { ByteArray(FileUtils.ONE_MB.toInt()) }
            Thread.sleep(5)     // make sure that files are exactly sortable by modification date
        }
        // now in cache: SOME_KEY (some bytes) and "0" .. "10" (1 MB each), i.e. 11 MB + some bytes in total
        assertEquals(MAX_CACHE_MB+2, cache.entries())

        // trim() should remove the oldest entries (SOME_KEY and "0") to trim to 10 MB
        assertEquals(2, cache.trim())

        // now in cache: "1" .. "10" = 10 MB
        assertEquals((1..MAX_CACHE_MB).map { it.toString() }.toSet(), cache.keys().toSet())
    }

}