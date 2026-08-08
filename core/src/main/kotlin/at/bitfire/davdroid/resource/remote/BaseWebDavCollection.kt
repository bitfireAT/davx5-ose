/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.resource.remote

import at.bitfire.dav4jvm.QuotedStringUtils
import at.bitfire.dav4jvm.ktor.DavResource
import at.bitfire.dav4jvm.ktor.MultiStatusItem
import at.bitfire.dav4jvm.ktor.exception.DavException
import at.bitfire.dav4jvm.ktor.responsesWithRelation
import at.bitfire.dav4jvm.ktor.selfResponse
import at.bitfire.dav4jvm.property.caldav.CalDAV
import at.bitfire.dav4jvm.property.caldav.ScheduleTag
import at.bitfire.dav4jvm.property.carddav.CardDAV
import at.bitfire.dav4jvm.property.carddav.SupportedAddressData
import at.bitfire.dav4jvm.property.webdav.GetETag
import at.bitfire.dav4jvm.property.webdav.SupportedReportSet
import at.bitfire.dav4jvm.property.webdav.WebDAV
import at.bitfire.davdroid.resource.SyncState
import io.ktor.client.HttpClient
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.Url
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headers
import io.ktor.util.appendAll
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import at.bitfire.dav4jvm.property.caldav.MaxResourceSize as CalDavMaxResourceSize
import at.bitfire.dav4jvm.property.carddav.MaxResourceSize as CardDavMaxResourceSize

/**
 * Common implementation of [WebDavCollection] for [CalDavCollection] and [CardDavCollection].
 */
abstract class BaseWebDavCollection(
    protected val httpClient: HttpClient,
    protected val url: Url
) : WebDavCollection {

    // create

    override suspend fun createMember(
        fileName: String,
        content: OutgoingContent,
        additionalHeaders: Map<String, String>
    ): WebDavCollection.PutMemberResult =
        putMember(
            fileName,
            content,
            additionalHeaders = headers {
                // fail if a member with that file name already exists
                append(HttpHeaders.IfNoneMatch, "*")

                appendAll(additionalHeaders)
            }
        )


    // read/query

    override suspend fun queryCapabilities(): WebDavCollection.QueryCapabilitiesResult {
        val response = davCollection.propfind(
            depth = 0,
            // WebDAV
            WebDAV.SupportedReportSet,
            WebDAV.SyncToken,
            // CalDAV
            CalDAV.GetCTag,
            CalDAV.MaxResourceSize,
            // CardDAV
            CardDAV.MaxResourceSize,
            CardDAV.SupportedAddressData
        ).selfResponse() ?: return WebDavCollection.QueryCapabilitiesResult(
            syncState = null,
            capabilities = WebDavCollection.Capabilities()
        )

        return WebDavCollection.QueryCapabilitiesResult(
            syncState = response.syncState(),
            capabilities = WebDavCollection.Capabilities(
                canCollectionSync = response[SupportedReportSet::class.java]?.reports?.contains(WebDAV.SyncCollection) == true,
                maxCalResourceSize = response[CalDavMaxResourceSize::class.java]?.maxSize,
                maxCardResourceSize = response[CardDavMaxResourceSize::class.java]?.maxSize,
                supportsVCard4 = response[SupportedAddressData::class.java]?.hasVCard4() == true
            )
        )
    }

    override suspend fun querySyncState(): SyncState? =
        davCollection.propfind(depth = 0, CalDAV.GetCTag, WebDAV.SyncToken).selfResponse()?.syncState()

    /**
     * Maps the member responses of a PROPFIND/REPORT to [MemberState]s, dropping everything
     * that isn't an actual member: the collection itself, sub-collections and unsuccessful
     * responses.
     *
     * @throws DavException if a member is listed without ETag
     */
    protected fun Flow<MultiStatusItem>.toMemberStates(): Flow<MemberState> =
        responsesWithRelation()
            .filterMembers()
            .filterNotCollections()
            .filterSuccessful()
            .map { item ->
                MemberState(
                    href = item.response.href,
                    eTag = item.response[GetETag::class.java]?.eTag
                        ?: throw DavException("Server didn't provide ETag for ${item.response.href}")
                )
            }


    // update

    override suspend fun updateMember(
        fileName: String,
        content: OutgoingContent,
        ifETag: String?,
        ifScheduleTag: String?,
        additionalHeaders: Map<String, String>
    ): WebDavCollection.PutMemberResult =
        putMember(
            fileName,
            content,
            additionalHeaders = headers {
                if (ifETag != null) {
                    // only update specific version
                    append(HttpHeaders.IfMatch, QuotedStringUtils.asQuotedString(ifETag))
                }
                if (ifScheduleTag != null) {
                    // only update specific version
                    append(HttpHeaders.IfScheduleTagMatch, QuotedStringUtils.asQuotedString(ifScheduleTag))
                }

                appendAll(additionalHeaders)
            }
        )


    // delete

    override suspend fun deleteMember(
        fileName: String,
        ifETag: String?,
        ifScheduleTag: String?,
        additionalHeaders: Map<String, String>
    ) {
        DavResource(httpClient, url.member(fileName)).delete(
            additionalHeaders = headers {
                if (ifETag != null) {
                    // only delete specific version
                    append(HttpHeaders.IfMatch, QuotedStringUtils.asQuotedString(ifETag))
                }
                if (ifScheduleTag != null) {
                    // only delete specific version
                    append(HttpHeaders.IfScheduleTagMatch, QuotedStringUtils.asQuotedString(ifScheduleTag))
                }

                appendAll(additionalHeaders)
            }
        ) {
            // don't do anything special on success
        }
    }


    // request helpers

    private suspend fun putMember(
        fileName: String,
        content: OutgoingContent,
        additionalHeaders: Headers
    ): WebDavCollection.PutMemberResult =
        DavResource(httpClient, url.member(fileName)).put(content, additionalHeaders) { response ->
            WebDavCollection.PutMemberResult(
                eTag = GetETag.fromHttpResponse(response)?.eTag,
                scheduleTag = ScheduleTag.fromHttpResponse(response)?.scheduleTag
            )
        }

}
