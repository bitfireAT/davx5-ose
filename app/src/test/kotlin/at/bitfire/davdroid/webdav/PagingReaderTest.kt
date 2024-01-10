/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.webdav

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 In the context of these tests, a "small file" is a file that is smaller
 than the max page size. A "page-sized file" is a file that is exactly as
 large as the page size. A "large file" is a file that is larger than the page
 size, i.e. a file that comprises at least two pages.
 */
class PagingReaderTest {

    @Test
    fun testRead_AcrossThreePages() {
        var idx = 0
        val reader = PagingReader(350, 100) { offset, size ->
            assertEquals(idx * 100L, offset)
            assertEquals(
                when (idx) {
                    0, 1, 2 -> 100
                    3 -> 50
                    else -> throw AssertionError("idx=$idx, size=$size")
                },
                size
            )
            idx += 1
            ByteArray(size) { idx.toByte() }
        }
        val dst = ByteArray(103)
        assertEquals(103, reader.read(99, 103, dst))
        assertArrayEquals(
            ByteArray(1) { 1 } + ByteArray(100) { 2 } + ByteArray(2) { 3 },
            dst
        )
    }

    @Test
    fun testRead_AtBeginning_FewBytes() {
        val reader = PagingReader(200, 100) { offset, size ->
            assertEquals(0, offset)
            assertEquals(100, size)
            ByteArray(100) { 1 }
        }
        val dst = ByteArray(10)
        assertEquals(10, reader.read(0, 10, dst))
        assertArrayEquals(ByteArray(10) { 1 }, dst)
    }

    @Test
    fun testRead_AtEOF() {
        val reader = PagingReader(200, 100) { _, _ ->
            throw AssertionError("Must not be called with size=0")
        }
        assertEquals(0, reader.read(200, 10, ByteArray(10)))
    }


    @Test
    fun testReadPage_LargeFile_FromMid_ToMid() {
        val reader = PagingReader(200, 100) { offset, size ->
            assertEquals(0, offset)
            assertEquals(100, size)
            ByteArray(100) { 1 }
        }
        val dst = ByteArray(10)
        assertEquals(10, reader.readPage(50, 10, dst, 0))
        assertArrayEquals(ByteArray(10) { 1 }, dst)
    }

    @Test
    fun testReadPage_LargeFile_FromMid_BeyondPage() {
        val reader = PagingReader(200, 100) { offset, size ->
            assertEquals(0, offset)
            assertEquals(100, size)
            ByteArray(100) { 1 }
        }
        val dst = ByteArray(100)
        assertEquals(50, reader.readPage(50, 100, dst, 0))
        assertArrayEquals(ByteArray(50) { 1 }, dst.copyOfRange(0, 50))
    }

    @Test
    fun testReadPage_LargeFile_FromStart_LessThanAPage() {
        val reader = PagingReader(200, 100) { offset, size ->
            assertEquals(0, offset)
            assertEquals(100, size)
            ByteArray(100) { 1 }
        }
        val dst = ByteArray(10)
        assertEquals(10, reader.readPage(0, 10, dst, 0))
        assertArrayEquals(ByteArray(10) { 1 }, dst)
    }

    @Test
    fun testReadPage_LargeFile_FromStart_OnePage() {
        val reader = PagingReader(200, 100) { offset, size ->
            assertEquals(0, offset)
            assertEquals(100, size)
            ByteArray(100) { 1 }
        }
        val dst = ByteArray(100)
        assertEquals(100, reader.readPage(0, 100, dst, 0))
        assertArrayEquals(ByteArray(100) { 1 }, dst)
    }

    @Test
    fun testReadPage_LargeFile_FromStart_MoreThanAvailable() {
        val reader = PagingReader(200, 100) { offset, size ->
            assertEquals(0, offset)
            assertEquals(100, size)
            ByteArray(100) { 1 }
        }
        val dst = ByteArray(200)
        assertEquals(100, reader.readPage(0, 200, dst, 100))
        assertArrayEquals(ByteArray(100) { 1 }, dst.copyOfRange(100, 200))
    }


    @Test
    fun testReadPage_PageSizedFile_FromEnd() {
        val reader = PagingReader(100, 100) { _, _ ->
            throw AssertionError()
        }
        val dst = ByteArray(100)
        assertEquals(0, reader.readPage(100, 100, dst, 0))
        assertArrayEquals(ByteArray(100), dst)
    }

    @Test
    fun testReadPage_PageSizedFile_FromStart_Complete() {
        val reader = PagingReader(100, 100) { offset, size ->
            assertEquals(0, offset)
            assertEquals(100, size)
            ByteArray(100) { 1 }
        }
        val dst = ByteArray(100)
        assertEquals(100, reader.readPage(0, 100, dst, 0))
        assertArrayEquals(ByteArray(100) { 1 }, dst)
    }

    @Test
    fun testReadPage_PageSizedFile_FromStart_MoreThanAvailable() {
        val reader = PagingReader(100, 100) { offset, size ->
            assertEquals(0, offset)
            assertEquals(100, size)
            ByteArray(100) { 1 }
        }
        val dst = ByteArray(200)
        assertEquals(100, reader.readPage(0, 200, dst, 100))
        assertArrayEquals(ByteArray(100) { 1 }, dst.copyOfRange(100, 200))
    }


    @Test
    fun testReadPage_SmallFile_FromStart_Partial() {
        val reader = PagingReader(100, 1000) { offset, size ->
            assertEquals(0, offset)
            assertEquals(100, size)
            ByteArray(100) { 1 }
        }
        val dst = ByteArray(10)
        assertEquals(10, reader.readPage(0, 10, dst, 0))
        assertArrayEquals(dst, ByteArray(10) { 1 })
    }

    @Test
    fun testReadPage_SmallFile_FromStart_Complete() {
        val reader = PagingReader(100, 1000) { offset, size ->
            assertEquals(0, offset)
            assertEquals(100, size)
            ByteArray(100) { 1 }
        }
        val dst = ByteArray(100)
        assertEquals(100, reader.readPage(0, 100, dst, 0))
        assertArrayEquals(ByteArray(100) { 1 }, dst)
    }

    @Test
    fun testReadPage_SmallFile_FromStart_MoreThanAvailable() {
        val reader = PagingReader(100, 1000) { offset, size ->
            assertEquals(0, offset)
            assertEquals(100, size)
            ByteArray(100) { 1 }
        }
        val dst = ByteArray(200)
        assertEquals(100, reader.readPage(0, 200, dst, 100))
        assertArrayEquals(ByteArray(100) { 1 }, dst.copyOfRange(100, 200))
    }

}