/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.sync

import at.bitfire.davdroid.resource.remote.WebDavCollection
import io.ktor.http.Url
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

// TODO: rewrite against the new downloadBatch()/multiget() API
class BatchDownloaderTest {

    private fun url(i: Int) = Url("https://example.com/$i")

    private fun item(url: Url) = WebDavCollection.MultiGetItem(url, eTag = "etag", content = "content")

    @Test
    fun testDownloadBatch_GroupsUrlsAndCallsMultigetPerBatch() = runTest {
        val batches = mutableListOf<List<Url>>()

        val result = (1..5).map { url(it) }.asFlow()
            .downloadBatch(batchSize = 2) { batch ->
                batches += batch
                flow { batch.forEach { emit(item(it)) } }
            }
            .toList()

        assertEquals(listOf(2, 2, 1), batches.map { it.size })
        assertEquals(5, result.size)
    }

}
