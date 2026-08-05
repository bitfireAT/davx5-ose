/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.resource.remote

import at.bitfire.dav4jvm.QuotedStringUtils
import at.bitfire.dav4jvm.ktor.DavCollection
import at.bitfire.dav4jvm.ktor.DavResource
import at.bitfire.dav4jvm.ktor.MultiStatusItem
import at.bitfire.dav4jvm.ktor.Response
import at.bitfire.dav4jvm.ktor.selfResponse
import at.bitfire.dav4jvm.property.caldav.CalDAV
import at.bitfire.dav4jvm.property.caldav.ScheduleTag
import at.bitfire.dav4jvm.property.carddav.CardDAV
import at.bitfire.dav4jvm.property.carddav.SupportedAddressData
import at.bitfire.dav4jvm.property.webdav.GetETag
import at.bitfire.dav4jvm.property.webdav.SupportedReportSet
import at.bitfire.dav4jvm.property.webdav.SyncToken
import at.bitfire.dav4jvm.property.webdav.WebDAV
import at.bitfire.davdroid.resource.SyncState
import at.bitfire.davdroid.resource.syncState
import at.bitfire.davdroid.sync.withExceptionContext
import io.ktor.client.HttpClient
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.URLBuilder
import io.ktor.http.Url
import io.ktor.http.appendPathSegments
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headers
import io.ktor.util.appendAll
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.transform
import java.util.logging.Logger
import at.bitfire.dav4jvm.property.caldav.MaxResourceSize as CalDavMaxResourceSize
import at.bitfire.dav4jvm.property.carddav.MaxResourceSize as CardDavMaxResourceSize

/**
 * Common implementation for [CalDavCollection] and [CardDavCollection], based on dav4jvm.
 */
abstract class BaseWebDavCollection(
    val davCollection: DavCollection,
    private val httpClient: HttpClient,
    private val pushSubscription: String? = null
) : WebDavCollection {

    protected val logger: Logger = Logger.getLogger(javaClass.name)

    override val url: Url
        get() = davCollection.location

    /**
     * Push-Dont-Notify header, added to PUT and DELETE requests if a push subscription exists.
     */
    private val pushDontNotifyHeader by lazy {
        pushSubscription?.let { subscription ->
            mapOf("Push-Dont-Notify" to QuotedStringUtils.asQuotedString(subscription))
        } ?: emptyMap()
    }

    override suspend fun queryCapabilities(): WebDavCollection.Capabilities =
        url.withExceptionContext {
            val response = davCollection.propfind(
                0,
                WebDAV.SupportedReportSet,
                CalDAV.GetCTag,
                WebDAV.SyncToken,
                CalDAV.MaxResourceSize,
                CardDAV.MaxResourceSize,
                CardDAV.SupportedAddressData
            ).selfResponse() ?: return@withExceptionContext WebDavCollection.Capabilities()

            WebDavCollection.Capabilities(
                syncState = response.syncState(),
                supportsCollectionSync = response[SupportedReportSet::class.java]?.reports?.contains(WebDAV.SyncCollection) == true,
                maxResourceSize = response[CalDavMaxResourceSize::class.java]?.maxSize
                    ?: response[CardDavMaxResourceSize::class.java]?.maxSize,
                supportsVCard4 = response[SupportedAddressData::class.java]?.hasVCard4() == true
            )
        }

    override suspend fun querySyncState(): SyncState? =
        url.withExceptionContext {
            davCollection.propfind(0, CalDAV.GetCTag, WebDAV.SyncToken).selfResponse()?.syncState()
        }

    override fun listChanges(syncToken: String?): Pair<Flow<MultiStatusItem>, WebDavCollection.SyncCollectionResult> {
        val result = WebDavCollection.SyncCollectionResult()
        val flow = davCollection.reportChanges(
            syncToken = syncToken,
            infiniteDepth = false,
            limit = null,
            WebDAV.GetETag
        ).transform { item ->
            when (item) {
                is MultiStatusItem.Response -> when (item.relation) {
                    Response.HrefRelation.SELF -> {
                        // incoming self response, update result
                        result.furtherResults = item.response.status == HttpStatusCode.InsufficientStorage
                    }

                    Response.HrefRelation.MEMBER -> {
                        // incoming (changed/deleted) member response, emit to flow
                        emit(item)
                    }

                    else ->
                        logger.warning("Unexpected sync-collection response: ${item.response}")
                }

                is MultiStatusItem.ExtraProperty -> {
                    // incoming sync-token, update result
                    (item.property as? SyncToken)?.let { result.syncToken = it.token }
                }
            }
        }
        return flow to result
    }

    override suspend fun putMember(
        fileName: String,
        content: OutgoingContent,
        ifETag: String?,
        ifScheduleTag: String?,
        ifNoneMatch: Boolean
    ): WebDavCollection.UploadResult {
        val memberUrl = URLBuilder(url).appendPathSegments(fileName, encodeSlash = true).build()
        val remote = DavResource(httpClient, memberUrl)

        var eTag: String? = null
        var scheduleTag: String? = null
        memberUrl.withExceptionContext {
            remote.put(
                content,
                ifETag = ifETag,
                ifScheduleTag = ifScheduleTag,
                ifNoneMatch = ifNoneMatch,
                headers = pushDontNotifyHeader,
                callback = { response ->
                    eTag = GetETag.fromHttpResponse(response)?.eTag
                    scheduleTag = ScheduleTag.fromHttpResponse(response)?.scheduleTag
                }
            )
        }
        return WebDavCollection.UploadResult(eTag, scheduleTag)
    }

    override suspend fun deleteMember(fileName: String, ifETag: String?, ifScheduleTag: String?) {
        val memberUrl = URLBuilder(url).appendPathSegments(fileName, encodeSlash = true).build()
        val remote = DavResource(httpClient, memberUrl)

        memberUrl.withExceptionContext {
            remote.delete(
                ifETag = ifETag,
                ifScheduleTag = ifScheduleTag,
                headers = pushDontNotifyHeader,
                callback = {}
            )
        }
    }


    /**
     * A wrapper for making `PUT` requests with conditional headers.
     * @param content The content to send in the PUT request.
     * @param ifETag If one is given, the `If-Match` header will have this value.
     * @param ifScheduleTag If one is given, the `If-Schedule-Tag-Match` header will have this value.
     * @param ifNoneMatch If `true`, the `If-None-Match` header will be set to `*`.
     * @param headers Any other headers to append to the request.
     * @param callback Will be called with the request's response.
     */
    private suspend fun DavResource.put(
        content: OutgoingContent,
        ifETag: String? = null,
        ifScheduleTag: String? = null,
        ifNoneMatch: Boolean = false,
        headers: Map<String, String> = emptyMap(),
        callback: suspend (HttpResponse) -> Unit
    ) {
        put(
            content,
            additionalHeaders = headers {
                if (ifETag != null)
                // only overwrite specific version
                    append(HttpHeaders.IfMatch, QuotedStringUtils.asQuotedString(ifETag))
                if (ifScheduleTag != null)
                // only overwrite specific version
                    append(HttpHeaders.IfScheduleTagMatch, QuotedStringUtils.asQuotedString(ifScheduleTag))
                if (ifNoneMatch)
                // don't overwrite anything existing
                    append(HttpHeaders.IfNoneMatch, "*")

                // Append all custom headers
                appendAll(headers)
            },
            callback = callback
        )
    }

    /**
     * A wrapper for making `DELETE` requests with conditional headers.
     * @param ifETag If one is given, the `If-Match` header will have this value.
     * @param ifScheduleTag If one is given, the `If-Schedule-Tag-Match` header will have this value.
     * @param headers Any other headers to append to the request.
     * @param callback Will be called with the request's response.
     */
    private suspend fun DavResource.delete(
        ifETag: String? = null,
        ifScheduleTag: String? = null,
        headers: Map<String, String> = emptyMap(),
        callback: suspend (HttpResponse) -> Unit
    ) {
        delete(
            additionalHeaders = headers {
                if (ifETag != null)
                    append(HttpHeaders.IfMatch, QuotedStringUtils.asQuotedString(ifETag))
                if (ifScheduleTag != null)
                    append(HttpHeaders.IfScheduleTagMatch, QuotedStringUtils.asQuotedString(ifScheduleTag))

                // Append all custom headers
                appendAll(headers)
            },
            callback = callback
        )
    }

}
