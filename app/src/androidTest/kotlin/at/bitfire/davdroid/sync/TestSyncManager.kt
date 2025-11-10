/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.sync

import android.accounts.Account
import at.bitfire.dav4jvm.okhttp.DavCollection
import at.bitfire.dav4jvm.okhttp.MultiResponseCallback
import at.bitfire.dav4jvm.okhttp.Response
import at.bitfire.dav4jvm.property.caldav.GetCTag
import at.bitfire.davdroid.db.Collection
import at.bitfire.davdroid.di.SyncDispatcher
import at.bitfire.davdroid.resource.LocalResource
import at.bitfire.davdroid.resource.SyncState
import at.bitfire.davdroid.util.DavUtils.lastSegment
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CoroutineDispatcher
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.junit.Assert.assertEquals

class TestSyncManager @AssistedInject constructor(
    @Assisted account: Account,
    @Assisted httpClient: OkHttpClient,
    @Assisted syncResult: SyncResult,
    @Assisted localCollection: LocalTestCollection,
    @Assisted collection: Collection,
    @SyncDispatcher syncDispatcher: CoroutineDispatcher
): SyncManager<LocalTestResource, LocalTestCollection, DavCollection>(
    account,
    httpClient,
    SyncDataType.EVENTS,
    syncResult,
    localCollection,
    collection,
    resync = null,
    syncDispatcher
) {

    @AssistedFactory
    interface Factory {
        fun create(
            account: Account,
            httpClient: OkHttpClient,
            syncResult: SyncResult,
            localCollection: LocalTestCollection,
            collection: Collection
        ): TestSyncManager
    }

    override fun prepare(): Boolean {
        davCollection = DavCollection(httpClient, collection.url)
        return true
    }

    var didQueryCapabilities = false
    override suspend fun queryCapabilities(): SyncState? {
        if (didQueryCapabilities)
            throw IllegalStateException("queryCapabilities() must not be called twice")
        didQueryCapabilities = true

        var cTag: SyncState? = null
        davCollection.propfind(0, GetCTag.NAME) { response, rel ->
            if (rel == Response.HrefRelation.SELF)
                response[GetCTag::class.java]?.cTag?.let {
                    cTag = SyncState(SyncState.Type.CTAG, it)
                }
        }

        return cTag
    }

    var didGenerateUpload = false
    override fun generateUpload(resource: LocalTestResource): GeneratedResource {
        didGenerateUpload = true
        return GeneratedResource(
            suggestedFileName = resource.fileName ?: "generated-file.txt",
            requestBody = resource.toString().toRequestBody(),
            onSuccessContext = GeneratedResource.OnSuccessContext()
        )
    }

    override fun syncAlgorithm() = SyncAlgorithm.PROPFIND_REPORT

    var listAllRemoteResult = emptyList<Pair<Response, Response.HrefRelation>>()
    var didListAllRemote = false
    override suspend fun listAllRemote(callback: MultiResponseCallback) {
        if (didListAllRemote)
            throw IllegalStateException("listAllRemote() must not be called twice")
        didListAllRemote = true
        for (result in listAllRemoteResult)
            callback.onResponse(result.first, result.second)
    }

    var assertDownloadRemote = emptyMap<HttpUrl, String>()
    var didDownloadRemote = false
    override suspend fun downloadRemote(bunch: List<HttpUrl>) {
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

    override fun postProcess() {
    }

    override fun notifyInvalidResourceTitle() =
        throw NotImplementedError()

}