/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.jtx

import at.bitfire.synctools.mapping.jtx.handler.AttachmentFetcher

class FakeAttachmentFetcher : AttachmentFetcher {
    var lastAttachmentId: Long? = null
        private set

    var attachmentData: ByteArray? = null

    override fun getAttachmentData(attachmentId: Long): ByteArray? {
        lastAttachmentId = attachmentId
        return attachmentData
    }
}
