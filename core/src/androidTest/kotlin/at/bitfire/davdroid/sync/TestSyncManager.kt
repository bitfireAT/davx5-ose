/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.sync

import android.accounts.Account
import at.bitfire.dav4jvm.ktor.DavCollection
import at.bitfire.dav4jvm.ktor.MultiStatusItem
import at.bitfire.dav4jvm.ktor.Response
import at.bitfire.dav4jvm.ktor.selfResponse
import at.bitfire.dav4jvm.property.caldav.CalDAV
import at.bitfire.dav4jvm.property.caldav.GetCTag
import at.bitfire.davdroid.db.Collection
import at.bitfire.davdroid.di.qualifier.IoDispatcher
import at.bitfire.davdroid.di.qualifier.SyncTransferSemaphore
import at.bitfire.davdroid.resource.LocalResource
import at.bitfire.davdroid.resource.SyncState
import at.bitfire.davdroid.util.DavUtils.lastSegment
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import io.ktor.client.HttpClient
import io.ktor.http.Url
import io.ktor.http.content.ByteArrayContent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Semaphore
import org.junit.Assert.assertEquals

class TestSyncManager @AssistedInject constructor(
    @Assisted account: Account,
    @Assisted httpClient: HttpClient,
    @Assisted syncResult: SyncResult,
    @Assisted localCollection: LocalTestCollection,
    @Assisted collection: Collection,
    @IoDispatcher ioDispatcher: CoroutineDispatcher,
    @SyncTransferSemaphore syncTransferSemaphore: Semaphore
): SyncManager<LocalTestResource, LocalTestCollection, DavCollection>(
    account,
    httpClient,
    SyncDataType.EVENTS,
    syncResult,
    localCollection,
    collection,
    resync = null,
    ioDispatcher,
    syncTransferSemaphore
) {

    @AssistedFactory
    interface Factory {
        fun create(
            account: Account,
            httpClient: HttpClient,
            syncResult: SyncResult,
            localCollection: LocalTestCollection,
            collection: Collection
        ): TestSyncManager
    }

    override suspend fun prepare(): Boolean {
        davCollection = DavCollection(httpClient, collection.url)
        return true
    }

    var didQueryCapabilities = false
    override suspend fun queryCapabilities(): SyncState? {
        if (didQueryCapabilities)
            throw IllegalStateException("queryCapabilities() must not be called twice")
        didQueryCapabilities = true

        val response = davCollection.propfind(0, CalDAV.GetCTag).selfResponse()
        return response?.let { it[GetCTag::class.java]?.cTag }?.let {
            SyncState(SyncState.Type.CTAG, it)
        }
    }

    var didGenerateUpload = false
    override fun generateUpload(resource: LocalTestResource): GeneratedResource {
        didGenerateUpload = true
        return GeneratedResource(
            suggestedFileName = resource.fileName ?: "generated-file.txt",
            content = ByteArrayContent(
                bytes = resource.toString().encodeToByteArray()
            ),
            onSuccessContext = GeneratedResource.OnSuccessContext()
        )
    }

    override fun syncAlgorithm() = propfindReportAlgorithm()

    var listAllRemoteResult = emptyList<Pair<Response, Response.HrefRelation>>()
    var didListAllRemote = false
    override fun listAllRemote(): Flow<MultiStatusItem> {
        if (didListAllRemote)
            throw IllegalStateException("listAllRemote() must not be called twice")
        didListAllRemote = true
        return listAllRemoteResult.asFlow().map { MultiStatusItem.Response(it.first, it.second) }
    }

    var assertDownloadRemote = emptyMap<Url, String>()
    var didDownloadRemote = false
    override suspend fun downloadRemote(bunch: List<Url>) {
        didDownloadRemote = true
        assertEquals(assertDownloadRemote.keys.toList(), bunch)

        for ((url, eTag) in assertDownloadRemote) {
            val fileName = url.lastSegment
            var localEntry = localCollection.entries.firstOrNull { it.fileName == fileName }
            if (localEntry == null) {
                val newEntry = LocalTestResource().also {
                    it.fileName = fileName
                }
                localCollection.entries += newEntry
                localEntry = newEntry
            }
            localEntry.eTag = eTag
            localEntry.flags = LocalResource.FLAG_REMOTELY_PRESENT
        }
    }

    override suspend fun postProcess() {
    }

    override fun notifyInvalidResourceTitle() =
        throw NotImplementedError()

}