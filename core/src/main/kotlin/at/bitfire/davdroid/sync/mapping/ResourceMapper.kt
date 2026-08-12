/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.sync.mapping

import androidx.annotation.VisibleForTesting
import at.bitfire.davdroid.resource.LocalResource
import at.bitfire.davdroid.resource.remote.WebDavCollection
import at.bitfire.davdroid.sync.GeneratedResource

/**
 * Maps a local resource to the request body used to upload it.
 *
 * Currently covers only the upload direction; intended to grow to include download/store
 * as those responsibilities move out of [at.bitfire.davdroid.sync.SyncManager] subclasses, too.
 */
interface ResourceMapper<LocalType : LocalResource> {

    /**
     * Generates the request body (iCalendar or vCard) from a local resource.
     *
     * @param resource      local resource to generate the body from
     * @param capabilities  current capabilities of the remote collection
     *
     * @return iCalendar or vCard (content + Content-Type) that can be uploaded to the server
     */
    @VisibleForTesting
    fun generateUpload(resource: LocalType, capabilities: WebDavCollection.Capabilities): GeneratedResource

}