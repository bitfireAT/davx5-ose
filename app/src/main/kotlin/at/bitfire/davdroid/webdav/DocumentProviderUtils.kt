/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.webdav

import android.app.AuthenticationRequiredException
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.DocumentsContract.buildChildDocumentsUri
import android.provider.DocumentsContract.buildRootsUri
import android.webkit.MimeTypeMap
import androidx.core.app.TaskStackBuilder
import at.bitfire.dav4jvm.okhttp.exception.HttpException
import at.bitfire.davdroid.R
import at.bitfire.davdroid.ui.webdav.WebdavMountsActivity
import java.io.FileNotFoundException

object DocumentProviderUtils  {

    const val MAX_DISPLAYNAME_TO_MEMBERNAME_ATTEMPTS = 5

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
        if (parentDocumentId != null)
            context.contentResolver.notifyChange(
                buildChildDocumentsUri(
                    context.getString(R.string.webdav_authority),
                    parentDocumentId.toString()
                ),
                null
            )
    }

    internal fun notifyFolderChanged(context: Context, parentDocumentId: String) {
        context.contentResolver.notifyChange(
            buildChildDocumentsUri(
                context.getString(R.string.webdav_authority),
                parentDocumentId
            ),
            null
        )
    }

    internal fun notifyMountsChanged(context: Context) {
        context.contentResolver.notifyChange(
            buildRootsUri(context.getString(R.string.webdav_authority)),
            null)
    }

}

internal fun HttpException.throwForDocumentProvider(context: Context, ignorePreconditionFailed: Boolean = false) {
    when (statusCode) {
        401 -> {
            if (Build.VERSION.SDK_INT >= 26) {
                val intent = Intent(context, WebdavMountsActivity::class.java)
                throw AuthenticationRequiredException(
                    this,
                    TaskStackBuilder.create(context)
                        .addNextIntentWithParentStack(intent)
                        .getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                )
            }
        }
        404 ->
            throw FileNotFoundException()
        412 ->
            if (ignorePreconditionFailed)
                return
    }

    // re-throw
    throw this
}