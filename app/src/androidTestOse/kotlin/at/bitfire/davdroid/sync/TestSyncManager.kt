/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.sync

import android.accounts.Account
import android.content.Context
import android.content.SyncResult
import at.bitfire.dav4jvm.DavCollection
import at.bitfire.dav4jvm.MultiResponseCallback
import at.bitfire.dav4jvm.Response
import at.bitfire.dav4jvm.property.caldav.GetCTag
import at.bitfire.davdroid.db.AppDatabase
import at.bitfire.davdroid.db.Collection
import at.bitfire.davdroid.db.SyncState
import at.bitfire.davdroid.network.HttpClient
import at.bitfire.davdroid.resource.LocalResource
import at.bitfire.davdroid.settings.AccountSettings
import at.bitfire.davdroid.util.lastSegment
import okhttp3.HttpUrl
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals

class TestSyncManager(
    account: Account,
    extras: Array<String>,
    authority: String,
    httpClient: HttpClient,
    syncResult: SyncResult,
    localCollection: LocalTestCollection,
    collection: Collection,
    val mockWebServer: MockWebServer,
    context: Context,
    db: AppDatabase
): SyncManager<LocalTestResource, LocalTestCollection, DavCollection>(account, AccountSettings(context, account), httpClient, extras, authority, syncResult, localCollection, collection, context, db) {

    override fun prepare(): Boolean {
        collectionURL = mockWebServer.url("/")
        davCollection = DavCollection(httpClient.okHttpClient, collectionURL)
        return true
    }

    var didQueryCapabilities = false
    override fun queryCapabilities(): SyncState? {
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
    override fun generateUpload(resource: LocalTestResource): RequestBody {
        didGenerateUpload = true
        return resource.toString().toRequestBody()
    }

    override fun syncAlgorithm() = SyncAlgorithm.PROPFIND_REPORT

    var listAllRemoteResult = emptyList<Pair<Response, Response.HrefRelation>>()
    var didListAllRemote = false
    override fun listAllRemote(callback: MultiResponseCallback) {
        if (didListAllRemote)
            throw IllegalStateException("listAllRemote() must not be called twice")
        didListAllRemote = true
        for (result in listAllRemoteResult)
            callback.onResponse(result.first, result.second)
    }

    var assertDownloadRemote = emptyMap<HttpUrl, String>()
    var didDownloadRemote = false
    override fun downloadRemote(bunch: List<HttpUrl>) {
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