/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.sync

import android.accounts.Account
import at.bitfire.dav4jvm.okhttp.DavCollection
import at.bitfire.dav4jvm.okhttp.MultiResponseCallback
import at.bitfire.davdroid.db.Collection
import at.bitfire.davdroid.resource.LocalCollection
import at.bitfire.davdroid.resource.LocalResource
import at.bitfire.davdroid.sync.SyncManager.Companion.MAX_MULTIGET_RESOURCES
import io.mockk.mockk
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Semaphore
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.logging.Logger

class SyncManagerTest {

    // consumeDownloadChannel

    // re-initialized per test because JUnit creates a new class instance for each test method
    private val downloadedBatches = mutableListOf<List<HttpUrl>>()

    private val syncManager = object : SyncManager<LocalResource, LocalCollection<LocalResource>, DavCollection>(
        account = mockk<Account>(),
        httpClient = mockk(),
        dataType = SyncDataType.EVENTS,
        syncResult = SyncResult(),
        localCollection = mockk(),
        collection = Collection(type = Collection.TYPE_CALENDAR, url = "https://example.com/".toHttpUrl()),
        resync = null
    ) {
        override fun prepare() = true
        override suspend fun queryCapabilities() = null
        override fun generateUpload(resource: LocalResource): GeneratedResource = throw NotImplementedError()
        override fun syncAlgorithm() = SyncAlgorithm.PROPFIND_REPORT
        override suspend fun listAllRemote(callback: MultiResponseCallback) {}
        override suspend fun downloadRemote(bunch: List<HttpUrl>) {
            downloadedBatches += bunch
        }

        override fun postProcess() {}
        override fun notifyInvalidResourceTitle() = ""
    }.also {
        it.downloadSemaphore = Semaphore(Int.MAX_VALUE)
        it.logger = Logger.getLogger(SyncManagerTest::class.java.name)
    }

    private fun seedAndClose(channel: Channel<HttpUrl>, n: Int) {
        for (i in 1..n) channel.trySend("https://example.com/$i.ics".toHttpUrl())
        channel.close()
    }

    @Test
    fun `consumeDownloadChannel empty channel downloads nothing`() = runBlocking {
        val channel = Channel<HttpUrl>(Channel.UNLIMITED)
        channel.close()
        syncManager.consumeDownloadChannel(scope = this, channel = channel).join()
        assertTrue(downloadedBatches.isEmpty())
    }

    @Test
    fun `consumeDownloadChannel fewer than max results in one partial batch`() = runBlocking {
        val channel = Channel<HttpUrl>(Channel.UNLIMITED)
        seedAndClose(channel, 3)
        syncManager.consumeDownloadChannel(scope = this, channel = channel).join()
        assertEquals(listOf(3), downloadedBatches.map { it.size })
    }

    @Test
    fun `consumeDownloadChannel exactly max results in one full batch`() = runBlocking {
        val channel = Channel<HttpUrl>(Channel.UNLIMITED)
        seedAndClose(channel, MAX_MULTIGET_RESOURCES)
        syncManager.consumeDownloadChannel(scope = this, channel = channel).join()
        assertEquals(listOf(MAX_MULTIGET_RESOURCES), downloadedBatches.map { it.size })
    }

    @Test
    fun `consumeDownloadChannel one full batch plus remainder`() = runBlocking {
        val channel = Channel<HttpUrl>(Channel.UNLIMITED)
        seedAndClose(channel, MAX_MULTIGET_RESOURCES + 5)
        syncManager.consumeDownloadChannel(scope = this, channel = channel).join()
        assertEquals(listOf(MAX_MULTIGET_RESOURCES, 5), downloadedBatches.map { it.size })
    }

    @Test
    fun `consumeDownloadChannel exact multiple of max results in two full batches`() = runBlocking {
        val channel = Channel<HttpUrl>(Channel.UNLIMITED)
        seedAndClose(channel, MAX_MULTIGET_RESOURCES * 2)
        syncManager.consumeDownloadChannel(scope = this, channel = channel).join()
        assertEquals(listOf(MAX_MULTIGET_RESOURCES, MAX_MULTIGET_RESOURCES), downloadedBatches.map { it.size })
    }

}
