/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.sync

import io.ktor.http.Url
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.ConcurrentLinkedQueue

class BatchDownloaderTest {

    private fun url(i: Int) = Url("https://example.com/$i")

    @Test
    fun testEnqueue_BelowBatchSize_DoesNotDownload() = runTest {
        val batches = mutableListOf<List<Url>>()
        val downloader = BatchDownloader(batchSize = 3) { batches += it }

        downloader.enqueue(url(1))
        downloader.enqueue(url(2))

        assertTrue(batches.isEmpty())
    }

    @Test
    fun testEnqueue_ReachesBatchSize_DownloadsOnceAndClearsQueue() = runTest {
        val batches = mutableListOf<List<Url>>()
        val downloader = BatchDownloader(batchSize = 3) { batches += it }

        downloader.enqueue(url(1))
        downloader.enqueue(url(2))
        downloader.enqueue(url(3))

        assertEquals(listOf(listOf(url(1), url(2), url(3))), batches)

        // queue has been cleared, so a flush() has nothing to download
        downloader.flush()
        assertEquals(1, batches.size)
    }

    @Test
    fun testEnqueue_MultipleBatches_DownloadsEachFullBatchInOrder() = runTest {
        val batches = mutableListOf<List<Url>>()
        val downloader = BatchDownloader(batchSize = 2) { batches += it }

        for (i in 1..5)
            downloader.enqueue(url(i))

        // 5 items with batchSize=2 -> two full batches downloaded so far, one item still queued
        assertEquals(
            listOf(
                listOf(url(1), url(2)),
                listOf(url(3), url(4))
            ),
            batches
        )
    }

    @Test
    fun testFlush_NonEmptyRemainder_DownloadsRemainder() = runTest {
        val batches = mutableListOf<List<Url>>()
        val downloader = BatchDownloader(batchSize = 10) { batches += it }

        downloader.enqueue(url(1))
        downloader.enqueue(url(2))
        downloader.flush()

        assertEquals(listOf(listOf(url(1), url(2))), batches)
    }

    @Test
    fun testFlush_EmptyQueue_DoesNotDownload() = runTest {
        val batches = mutableListOf<List<Url>>()
        val downloader = BatchDownloader(batchSize = 10) { batches += it }

        downloader.flush()

        assertTrue(batches.isEmpty())
    }

    @Test
    fun testPlusAssign_BehavesLikeEnqueue() = runTest {
        val batches = mutableListOf<List<Url>>()
        val downloader = BatchDownloader(batchSize = 1) { batches += it }

        downloader += url(1)

        assertEquals(listOf(listOf(url(1))), batches)
    }

    @Test
    fun testEnqueue_ConcurrentCalls_NoLostOrDuplicatedUrls() = runTest {
        val downloaded = ConcurrentLinkedQueue<Url>()
        val downloader = BatchDownloader(batchSize = 7) { downloaded += it }

        val urls = (1..50).map { url(it) }
        urls.map { u ->
            async { downloader.enqueue(u) }
        }.awaitAll()
        downloader.flush()

        assertEquals(urls.size, downloaded.size)
        assertEquals(urls.toSet(), downloaded.toSet())
    }

}
