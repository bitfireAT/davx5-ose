/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.sync.algorithm

import at.bitfire.dav4jvm.Property
import at.bitfire.dav4jvm.ktor.MultiStatusItem
import at.bitfire.davdroid.sync.MultiResponseCallback
import kotlinx.coroutines.flow.Flow

/**
 * Bridges a [Flow] of [MultiStatusItem]s back to callback style, invoking [callback]
 * for every [MultiStatusItem.Response] the flow emits.
 *
 * This is a temporary adapter to keep the sync algorithms close to their pre-[Flow] shape.
 * The goal is to migrate them to work with [Flow] directly and drop callback-style
 * processing (and this helper) entirely.
 *
 * @return properties found outside `<response>` elements (for instance `sync-token`)
 */
internal suspend fun Flow<MultiStatusItem>.forEachResponse(
    callback: MultiResponseCallback
): List<Property> {
    val extraProperties = mutableListOf<Property>()
    collect { item ->
        when (item) {
            is MultiStatusItem.Response -> callback(item.response, item.relation)
            is MultiStatusItem.ExtraProperty -> extraProperties += item.property
        }
    }
    return extraProperties
}
