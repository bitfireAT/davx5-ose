/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.jtx.handler

import android.content.ContentResolver
import androidx.core.net.toUri

class AndroidAttachmentFetcher(
    private val contentResolver: ContentResolver
) : AttachmentFetcher {
    override fun getAttachmentData(uri: String): ByteArray? {
        return contentResolver.openInputStream(uri.toUri())?.use { inputStream ->
            inputStream.readBytes()
        }
    }
}
