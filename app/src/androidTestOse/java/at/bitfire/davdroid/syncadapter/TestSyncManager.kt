/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.davdroid.syncadapter

import android.accounts.Account
import android.content.Context
import android.content.SyncResult
import android.os.Bundle
import at.bitfire.dav4jvm.DavCollection
import at.bitfire.dav4jvm.MultiResponseCallback
import at.bitfire.dav4jvm.Response
import at.bitfire.dav4jvm.property.GetCTag
import at.bitfire.davdroid.DavUtils
import at.bitfire.davdroid.HttpClient
import at.bitfire.davdroid.db.SyncState
import at.bitfire.davdroid.resource.LocalResource
import at.bitfire.davdroid.settings.AccountSettings
import okhttp3.HttpUrl
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals

class TestSyncManager(
        context: Context,
        account: Account,
        extras: Bundle,
        authority: String,
        httpClient: HttpClient,
        syncResult: SyncResult,
        localCollection: LocalTestCollection,
        val mockWebServer: MockWebServer
): SyncManager<LocalTestResource, LocalTestCollection, DavCollection>(context, account, AccountSettings(context, account), httpClient, extras, authority, syncResult, localCollection) {

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
            val fileName = DavUtils.lastSegmentOfUrl(url)
            var localEntry = localCollection.entries.filter { it.fileName == fileName }.firstOrNull()
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