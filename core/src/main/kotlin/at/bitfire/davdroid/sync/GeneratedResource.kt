/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.sync

import okhttp3.RequestBody

/**
 * Represents a resource that has been generated for the purpose of being uploaded.
 *
 * @param suggestedFileName     file name that can be used for uploading if there's no existing name
 * @param requestBody           resource body (including MIME type)
 * @param onSuccessContext      context that must be passed to [SyncManager.onSuccessfulUpload]
 * on successful upload in order to persist the changes made during mapping
 */
class GeneratedResource(
    val suggestedFileName: String,
    val requestBody: RequestBody,
    val onSuccessContext: OnSuccessContext? = null
) {

    /**
     * Contains information that has been created for a [GeneratedResource], but has not been saved yet.
     *
     * @param sequence  new SEQUENCE to persist on successful upload (*null*: SEQUENCE not modified)
     */
    data class OnSuccessContext(
        val sequence: Int? = null
    )

}