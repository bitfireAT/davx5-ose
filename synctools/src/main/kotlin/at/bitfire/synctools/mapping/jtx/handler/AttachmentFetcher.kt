/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.jtx.handler

interface AttachmentFetcher {
    fun getAttachmentData(attachmentId: Long): ByteArray?
}
