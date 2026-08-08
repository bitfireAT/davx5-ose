/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.resource.remote

import at.bitfire.dav4jvm.ktor.MultiStatusItem
import at.bitfire.dav4jvm.ktor.Response
import at.bitfire.dav4jvm.property.webdav.ResourceType
import at.bitfire.dav4jvm.property.webdav.WebDAV
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNot
import java.util.logging.Logger

private val logger = Logger.getLogger("at.bitfire.davdroid.resource.remote.MultiStatusItemExtensions")

/**
 * Filters this flow down to responses that are members of the collection (not the collection's
 * own response or an unrelated resource).
 *
 * Logs a warning for every response that gets filtered out.
 */
fun Flow<MultiStatusItem.Response>.filterMembers(): Flow<MultiStatusItem.Response> =
    filter { item ->
        val isMember = item.relation == Response.HrefRelation.MEMBER
        if (!isMember)
            logger.warning("Ignoring non-member response: ${item.response.href}")
        isMember
    }

/**
 * Filters this flow down to responses that are not collections.
 *
 * Only filters anything if [WebDAV.ResourceType] is present in the response.
 * **Make sure to request [WebDAV.ResourceType] if you want this filter to actually filter anything.**
 */
fun Flow<MultiStatusItem.Response>.filterNotCollections(): Flow<MultiStatusItem.Response> =
    filterNot { item -> item.response[ResourceType::class.java]?.types?.contains(WebDAV.Collection) == true }

/**
 * Filters this flow down to successful responses.
 *
 * Logs a warning for every response that gets filtered out.
 */
fun Flow<MultiStatusItem.Response>.filterSuccessful(): Flow<MultiStatusItem.Response> =
    filter { item ->
        val isSuccess = item.response.isSuccess()
        if (!isSuccess)
            logger.warning("Ignoring non-successful (${item.response.status}) response: ${item.response.href}")
        isSuccess
    }
