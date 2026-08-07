/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.resource.remote

import at.bitfire.dav4jvm.ktor.DavCollection
import at.bitfire.davdroid.resource.SyncState
import io.ktor.http.content.OutgoingContent

/**
 * Provides remote collection access as required for [at.bitfire.davdroid.sync.SyncManager].
 *
 * Currently, most call sites just call through [davCollection], but the goal is to provide
 * all remote collection operations that are required for synchronization here.
 */
interface WebDavCollection {

    val davCollection: DavCollection

    // create

    /**
     * Creates a new member (non-collection resource) of this collection (HTTP `PUT`).
     * Fails if a member with that file name already exists (`If-None-Match: *`).
     *
     * @param fileName          file name of the member to create
     * @param content           content to upload
     * @param additionalHeaders further headers to send (for instance `Push-Dont-Notify`)
     *
     * @return the new member's ETag / Schedule-Tag, if returned by the server
     *
     * @throws at.bitfire.dav4jvm.ktor.exception.PreconditionFailedException if a member with that file name already exists
     * @throws at.bitfire.dav4jvm.ktor.exception.HttpException on other HTTP errors
     */
    suspend fun createMember(
        fileName: String,
        content: OutgoingContent,
        additionalHeaders: Map<String, String> = emptyMap()
    ): PutMemberResult

    data class PutMemberResult(
        val eTag: String? = null,
        val scheduleTag: String? = null
    )


    // read/query

    /**
     * Queries the collection's current capabilities. Also fetches the [SyncState]
     * in the same PROPFIND request to save a network round-trip.
     *
     * @throws at.bitfire.dav4jvm.ktor.exception.HttpException on HTTP errors
     */
    suspend fun queryCapabilities(): QueryCapabilitiesResult

    data class QueryCapabilitiesResult(
        val syncState: SyncState?,
        val capabilities: Capabilities
    )

    data class Capabilities(
        /** whether the server supports Collection Sync (RFC 6578) */
        val canCollectionSync: Boolean = false,

        /** max. CalDAV resource size */
        val maxCalResourceSize: Long? = null,

        /** max. CardDAV resource size */
        val maxCardResourceSize: Long? = null,

        /** whether the (CardDAV) server supports vCard/4 */
        val supportsVCard4: Boolean = false
    )

    /**
     * Queries the collection's current [SyncState] (`sync-token` or `CTag`).
     *
     * @throws at.bitfire.dav4jvm.ktor.exception.HttpException on HTTP errors
     */
    suspend fun querySyncState(): SyncState?


    // update

    /**
     * Updates an existing member (non-collection resource) of this collection (HTTP `PUT`).
     *
     * @param fileName          file name of the member to update
     * @param content           new content to upload
     * @param ifETag            if given, sets `If-Match` so that the update only succeeds if the member's ETag matches
     * @param ifScheduleTag     if given, sets `If-Schedule-Tag-Match` so that the update only succeeds if the member's Schedule-Tag matches
     * @param additionalHeaders further headers to send (for instance `Push-Dont-Notify`)
     *
     * @return the updated member's ETag / Schedule-Tag, if returned by the server
     *
     * @throws at.bitfire.dav4jvm.ktor.exception.PreconditionFailedException if ifETag or ifScheduleTag is given and
     *   doesn't match the member's current state (also if the file doesn't exist on the server)
     * @throws at.bitfire.dav4jvm.ktor.exception.HttpException on other HTTP errors
     */
    suspend fun updateMember(
        fileName: String,
        content: OutgoingContent,
        ifETag: String? = null,
        ifScheduleTag: String? = null,
        additionalHeaders: Map<String, String> = emptyMap()
    ): PutMemberResult


    // delete

    /**
     * Deletes a member (non-collection resource) of this collection (HTTP `DELETE`).
     *
     * @param fileName          file name of the member to delete
     * @param ifETag            if given, sets `If-Match` so that the deletion only succeeds if the member's ETag matches
     * @param ifScheduleTag     if given, sets `If-Schedule-Tag-Match` so that the deletion only succeeds if the member's Schedule-Tag matches
     * @param additionalHeaders further headers to send (for instance `Push-Dont-Notify`)
     *
     * @throws at.bitfire.dav4jvm.ktor.exception.PreconditionFailedException if ifETag or ifScheduleTag is given and
     *   doesn't match the member's current state (also if the file doesn't exist on the server)
     * @throws at.bitfire.dav4jvm.ktor.exception.HttpException on HTTP errors
     */
    suspend fun deleteMember(
        fileName: String,
        ifETag: String? = null,
        ifScheduleTag: String? = null,
        additionalHeaders: Map<String, String> = emptyMap()
    )

}
