/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.sync.mapping

import at.bitfire.davdroid.sync.PendingLocalUpdate
import io.ktor.http.content.OutgoingContent

/**
 * Represents a resource that has been generated for the purpose of being uploaded.
 *
 * @param suggestedFileName     file name that can be used for uploading if there's no existing name
 * @param content               resource content (including MIME type)
 * @param pendingLocalUpdate    local resource properties that must be persisted by
 * [at.bitfire.davdroid.sync.SyncManager] once the upload has succeeded
 */
class GeneratedResource(
    val suggestedFileName: String,
    val content: OutgoingContent,
    val pendingLocalUpdate: PendingLocalUpdate? = null
)