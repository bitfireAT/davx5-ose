/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.sync

import at.bitfire.davdroid.resource.remote.WebDavCollection
import io.ktor.http.Url
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadBatchTest {

    private fun url(i: Int) = Url("https://example.com/$i")
    private fun item(url: Url) = WebDavCollection.MultiGetItem(url, eTag = "etag", content = "content")

    @Test
    fun `downloadBatch() with below-batch-size flow downloads remainder as one batch`() = runTest {
        val batches = mutableListOf<List<Url>>()

        val result = listOf(url(1), url(2)).asFlow()
            .downloadBatch(batchSize = 3) { batch ->
                batches += batch
                batch.map { item(it) }.asFlow()
            }.toList()

        assertEquals(listOf(listOf(url(1), url(2))), batches)
        assertEquals(listOf(item(url(1)), item(url(2))), result)
    }

    @Test
    fun `downloadBatch() with multiple batches downloads each full batch in order plus remainder`() = runTest {
        val batches = mutableListOf<List<Url>>()

        (1..5).map { url(it) }.asFlow()
            .downloadBatch(batchSize = 2) { batch ->
                batches += batch
                batch.map { item(it) }.asFlow()
            }.toList()

        assertEquals(
            listOf(
                listOf(url(1), url(2)),
                listOf(url(3), url(4)),
                listOf(url(5))
            ),
            batches
        )
    }

    @Test
    fun `downloadBatch() with batch size 1 downloads each url separately`() = runTest {
        val batches = mutableListOf<List<Url>>()

        listOf(url(1)).asFlow()
            .downloadBatch(batchSize = 1) { batch ->
                batches += batch
                batch.map { item(it) }.asFlow()
            }.toList()

        assertEquals(listOf(listOf(url(1))), batches)
    }

    @Test
    fun `downloadBatch() with empty flow does not download`() = runTest {
        val batches = mutableListOf<List<Url>>()

        emptyFlow<Url>()
            .downloadBatch(batchSize = 10) { batch ->
                batches += batch
                emptyFlow()
            }.toList()

        assertTrue(batches.isEmpty())
    }

}
