/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.sync

import at.bitfire.davdroid.accounts.AccountId
import at.bitfire.davdroid.db.Collection
import at.bitfire.davdroid.resource.local.LocalResource
import at.bitfire.davdroid.resource.remote.WebDavCollection
import at.bitfire.davdroid.util.DavUtils.extractFileName
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import io.ktor.client.HttpClient
import io.ktor.http.content.ByteArrayContent

class TestSyncManager @AssistedInject constructor(
    @Assisted accountId: AccountId,
    @Assisted httpClient: HttpClient,
    @Assisted syncResult: SyncResult,
    @Assisted override val localCollection: LocalTestCollection,
    @Assisted collectionInfo: Collection,
    @Assisted public override val remoteCollection: WebDavCollection,
    @Assisted settings: SyncSettings
) : SyncManager<LocalTestResource>(
    accountId,
    httpClient,
    SyncDataType.EVENTS,
    syncResult,
    collectionInfo,
    resync = null,
    settings
) {

    @AssistedFactory
    interface Factory {
        fun create(
            accountId: AccountId,
            httpClient: HttpClient,
            syncResult: SyncResult,
            localCollection: LocalTestCollection,
            collectionInfo: Collection,
            remoteCollection: WebDavCollection,
            settings: SyncSettings
        ): TestSyncManager
    }

    var didGenerateUpload = false
    override suspend fun generateUpload(
        resource: LocalTestResource,
        capabilities: WebDavCollection.Capabilities
    ): GeneratedResource {
        didGenerateUpload = true
        return GeneratedResource(
            suggestedFileName = resource.fileName ?: "generated-file.txt",
            content = ByteArrayContent(
                bytes = resource.toString().encodeToByteArray()
            ),
            onSuccessContext = GeneratedResource.OnSuccessContext()
        )
    }

    override fun syncAlgorithm(capabilities: WebDavCollection.Capabilities) = SyncAlgorithm.PROPFIND_REPORT

    var processedDownloads = emptyList<WebDavCollection.MultiGetItem>()
    override suspend fun processDownload(result: WebDavCollection.MultiGetItem) {
        processedDownloads += result

        val fileName = extractFileName(result.url)
        var localEntry = localCollection.entries.firstOrNull { it.fileName == fileName }
        if (localEntry == null) {
            val newEntry = LocalTestResource().also {
                it.fileName = fileName
            }
            localCollection.entries += newEntry
            localEntry = newEntry
        }
        localEntry.eTag = result.eTag
        localEntry.flags = LocalResource.FLAG_REMOTELY_PRESENT
    }

    override suspend fun postProcess() {
    }

}