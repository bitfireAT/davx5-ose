package at.bitfire.davdroid.webdav

import androidx.test.platform.app.InstrumentationRegistry
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.apache.commons.io.FileUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RandomAccessBufferTest {

    companion object {
        const val FILE_LENGTH = 10*FileUtils.ONE_MB
    }


    @Test
    fun testRead_FirstPage_PartialStart() {
        var called = false
        val buffer = newBuffer(object: RandomAccessBuffer.Reader {
            override fun readDirect(offset: Long, size: Int, dst: ByteArray): Int {
                assertEquals(0, offset)
                assertEquals(100, size)
                called = true
                return size
            }
        })
        val result = ByteArray(100)
        buffer.read(0, 100, result)
        assertTrue(called)
    }

    @Test
    fun testRead_FirstAndSecondPage_Overlapping() {
        var called = 0
        val buffer = newBuffer(object: RandomAccessBuffer.Reader {
            override fun readDirect(offset: Long, size: Int, dst: ByteArray): Int {
                // first page: 10 ... RandomAccessBuffer.PAGE_SIZE = (RandomAccessBuffer.PAGE_SIZE - 10) bytes
                // second page: 0 ... 110 = 110 bytes
                // in total = RandomAccessBuffer.PAGE_SIZE + 100 bytes
                when (called) {
                    0 -> {
                        assertEquals(10L, offset)
                        assertEquals(RandomAccessBuffer.PAGE_SIZE - 10, size)
                    }
                    1 -> {
                        assertEquals(RandomAccessBuffer.PAGE_SIZE.toLong(), offset)
                        assertEquals(110, size)
                    }
                }
                called++
                return size
            }
        })
        val result = ByteArray(RandomAccessBuffer.PAGE_SIZE + 100)
        buffer.read(10, RandomAccessBuffer.PAGE_SIZE + 100, result)
        assertEquals(2, called)
    }

    @Test
    fun testRead_SecondPage_Full() {
        var called = false
        val buffer = newBuffer(object: RandomAccessBuffer.Reader {
            override fun readDirect(offset: Long, size: Int, dst: ByteArray): Int {
                assertEquals(0L, offset)
                assertEquals(RandomAccessBuffer.PAGE_SIZE, size)
                called = true
                return size
            }
        })
        val result = ByteArray(RandomAccessBuffer.PAGE_SIZE)
        buffer.read(0, RandomAccessBuffer.PAGE_SIZE, result)
        assertTrue(called)
    }


    @Test
    fun testGetPageDirect_FullPage() {
        var called = false
        val buffer = newBuffer(object: RandomAccessBuffer.Reader {
            override fun readDirect(offset: Long, size: Int, dst: ByteArray): Int {
                assertEquals(0, offset)
                assertEquals(RandomAccessBuffer.PAGE_SIZE, size)
                called = true
                return size
            }
        })

        buffer.getPageDirect(0, 0, RandomAccessBuffer.PAGE_SIZE)
        assertTrue(called)
    }

    @Test
    fun testGetPageDirect_Partial() {
        var called = false
        val buffer = newBuffer(object: RandomAccessBuffer.Reader {
            override fun readDirect(offset: Long, size: Int, dst: ByteArray): Int {
                assertEquals(100, offset)
                assertEquals(200, size)
                called = true
                return size
            }
        })

        buffer.getPageDirect(0, 100, 200)
        assertTrue(called)
    }

    @Test(expected = IndexOutOfBoundsException::class)
    fun testGetPageDirect_Partial_StartingAtPageEnd() {
        val buffer = newBuffer(object: RandomAccessBuffer.Reader {
            override fun readDirect(offset: Long, size: Int, dst: ByteArray) = throw IllegalArgumentException()
        })

        val result = buffer.getPageDirect(0, FILE_LENGTH, 1)
    }

    @Test
    fun testGetPageDirect_Partial_LargerThanPage() {
        var called = false
        val buffer = newBuffer(object: RandomAccessBuffer.Reader {
            override fun readDirect(offset: Long, size: Int, dst: ByteArray): Int {
                called = true
                return size
            }
        })

        val result = buffer.getPageDirect(0, 100, FILE_LENGTH.toInt())
        assertTrue(called)
        assertEquals(result.size.toLong(), FILE_LENGTH - 100)
    }



    private fun newBuffer(reader: RandomAccessBuffer.Reader) =
        RandomAccessBuffer(
            InstrumentationRegistry.getInstrumentation().targetContext,
            "http://example.com/webdav".toHttpUrl(),
            FILE_LENGTH,
            null,
            reader
        )

}