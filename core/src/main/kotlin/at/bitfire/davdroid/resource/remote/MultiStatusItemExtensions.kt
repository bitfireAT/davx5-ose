/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.resource.remote

import at.bitfire.dav4jvm.ktor.MultiStatusItem
import at.bitfire.dav4jvm.ktor.Response
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import java.util.logging.Logger

/**
 * Filters this flow down to responses that
 *
 * - are members of the collection (not the collection's own response or an unrelated resource)
 * and successful.
 *
 * Logs a warning for every response that gets filtered out.
 */
fun Flow<MultiStatusItem.Response>.filterSuccessfulMembers(): Flow<MultiStatusItem.Response> {
    val logger = Logger.getLogger("at.bitfire.davdroid.resource.remote.MultiStatusItemExtensions")
    return filter { item ->
        val isMember = (item.relation == Response.HrefRelation.MEMBER)
        if (!isMember)
                logger.warning("Ignoring non-member multi-get response: ${item.response.href}")

        val isSuccess = item.response.isSuccess()
        if (!isSuccess)
            logger.warning("Ignoring non-successful (${item.response.status}) multi-get response: ${item.response.href}")

        isMember && isSuccess
    }
}
