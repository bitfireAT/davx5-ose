/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.resource.remote

import at.bitfire.dav4jvm.ktor.MultiStatusItem
import at.bitfire.davdroid.resource.SyncState
import io.ktor.http.Url
import io.ktor.http.content.OutgoingContent
import kotlinx.coroutines.flow.Flow

/**
 * Provides remote collection access as required for [at.bitfire.davdroid.sync.SyncManager].
 */
interface WebDavCollection {

    /** location of the collection */
    val url: Url

    /**
     * Queries the collection's capabilities and current sync state.
     */
    suspend fun queryCapabilities(): Capabilities

    /**
     * Queries only the current sync state (`CTag`/`sync-token`).
     */
    suspend fun querySyncState(): SyncState?

    /**
     * Lists all members of the collection (collection-type specific: calendar-query / PROPFIND depth 1).
     */
    fun listAll(): Flow<MultiStatusItem>

    /**
     * Lists changes since [syncToken] (RFC 6578); `null` token means an initial sync.
     */
    fun listChanges(syncToken: String?): Pair<Flow<MultiStatusItem>, SyncCollectionResult>

    /**
     * Downloads the given members (multi-get). The caller is responsible for parsing the responses.
     */
    fun downloadMembers(hrefs: List<Url>, capabilities: Capabilities): Flow<MultiStatusItem>

    /**
     * Uploads a member.
     *
     * @param fileName          file name of the member (relative to [url])
     * @param content           request body to upload
     * @param ifETag            if given, the upload is conditional on the member's current `ETag`
     * @param ifScheduleTag     if given, the upload is conditional on the member's current `Schedule-Tag`
     * @param ifNoneMatch       if `true`, the upload fails if a member with that file name already exists
     *
     * @return resulting `ETag`/`Schedule-Tag`, as reported by the server
     */
    suspend fun putMember(
        fileName: String,
        content: OutgoingContent,
        ifETag: String? = null,
        ifScheduleTag: String? = null,
        ifNoneMatch: Boolean = false
    ): UploadResult

    /**
     * Deletes a member.
     *
     * @param fileName          file name of the member (relative to [url])
     * @param ifETag            if given, the deletion is conditional on the member's current `ETag`
     * @param ifScheduleTag     if given, the deletion is conditional on the member's current `Schedule-Tag`
     */
    suspend fun deleteMember(fileName: String, ifETag: String? = null, ifScheduleTag: String? = null)


    data class UploadResult(val eTag: String?, val scheduleTag: String?)

    /**
     * Result of [queryCapabilities] — shared by all implementations; properties that a specific
     * collection type doesn't report keep their default value.
     */
    data class Capabilities(
        val syncState: SyncState? = null,
        val maxResourceSize: Long? = null,
        val supportsCollectionSync: Boolean = false,
        /** CardDAV only */
        val supportsVCard4: Boolean = false
    )

    /**
     * Holds the sync-token / further-results reported by a `sync-collection` REPORT.
     */
    class SyncCollectionResult {
        var syncToken: String? = null
        var furtherResults: Boolean = false
    }

}
