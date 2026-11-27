/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.webdav

import android.content.Context
import android.provider.DocumentsContract.buildChildDocumentsUri
import android.provider.DocumentsContract.buildRootsUri
import android.webkit.MimeTypeMap
import at.bitfire.davdroid.R
import java.util.logging.Logger

object DocumentProviderUtils {

    const val MAX_DISPLAYNAME_TO_MEMBERNAME_ATTEMPTS = 5

    private val logger
        get() = Logger.getLogger(javaClass.name)

    internal fun displayNameToMemberName(displayName: String, appendNumber: Int = 0): String {
        val safeName = displayName.filterNot { it.isISOControl() }

        if (appendNumber != 0) {
            val extension: String? = MimeTypeMap.getFileExtensionFromUrl(displayName)
            if (extension != null) {
                val baseName = safeName.removeSuffix(".$extension")
                return "${baseName}_$appendNumber.$extension"
            } else
                return "${safeName}_$appendNumber"
        } else
            return safeName
    }

    internal fun notifyFolderChanged(context: Context, parentDocumentId: Long?) {
        if (parentDocumentId != null) {
            val uri = buildChildDocumentsUri(
                context.getString(R.string.webdav_authority),
                parentDocumentId.toString()
            )
            logger.fine("Notifying observers of $uri")
            context.contentResolver.notifyChange(uri, null)
        }
    }

    internal fun notifyFolderChanged(context: Context, parentDocumentId: String) {
        val uri = buildChildDocumentsUri(
            context.getString(R.string.webdav_authority),
            parentDocumentId
        )
        logger.fine("Notifying observers of $uri")
        context.contentResolver.notifyChange(uri, null)
    }

    internal fun notifyMountsChanged(context: Context) {
        context.contentResolver.notifyChange(
            buildRootsUri(context.getString(R.string.webdav_authority)),
            null)
    }

}
