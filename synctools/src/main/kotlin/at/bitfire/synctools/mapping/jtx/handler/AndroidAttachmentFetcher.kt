/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.jtx.handler

import android.accounts.Account
import android.content.ContentProviderClient
import android.content.ContentUris
import android.os.ParcelFileDescriptor
import at.techbee.jtx.JtxContract
import at.techbee.jtx.JtxContract.asSyncAdapter
import java.util.logging.Level
import java.util.logging.Logger

class AndroidAttachmentFetcher(
    private val client: ContentProviderClient,
    private val account: Account
) : AttachmentFetcher {

    private val logger
        get() = Logger.getLogger(javaClass.name)

    override fun getAttachmentData(attachmentId: Long): ByteArray? {
        return try {
            val uri = ContentUris.withAppendedId(
                JtxContract.JtxAttachment.CONTENT_URI.asSyncAdapter(account),
                attachmentId
            )

            client.openFile(uri, "r")?.let { parcelFileDescriptor ->
                ParcelFileDescriptor.AutoCloseInputStream(parcelFileDescriptor).use { inputSteam ->
                    inputSteam.readBytes()
                }
            }
        } catch (e: Exception) {
            logger.log(Level.WARNING, "Error fetching attachment data: $attachmentId", e)
            null
        }
    }
}
