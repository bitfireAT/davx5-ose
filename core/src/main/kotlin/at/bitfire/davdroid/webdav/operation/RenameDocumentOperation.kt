/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.webdav.operation

import android.content.Context
import at.bitfire.dav4jvm.ktor.DavResource
import at.bitfire.dav4jvm.ktor.allSuccessful
import at.bitfire.dav4jvm.ktor.exception.DavException
import at.bitfire.dav4jvm.ktor.exception.PreconditionFailedException
import at.bitfire.dav4jvm.property.webdav.WebDAV
import at.bitfire.davdroid.db.AppDatabase
import at.bitfire.davdroid.db.WebDavDocument
import at.bitfire.davdroid.webdav.DavHttpClientBuilder
import at.bitfire.davdroid.webdav.DocumentProviderUtils
import at.bitfire.davdroid.webdav.DocumentProviderUtils.displayNameToMemberName
import dagger.hilt.android.qualifiers.ApplicationContext
import io.ktor.client.HttpClient
import java.io.FileNotFoundException
import java.util.logging.Level
import java.util.logging.Logger
import javax.inject.Inject

class RenameDocumentOperation @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: AppDatabase,
    private val davClientBuilder: DavHttpClientBuilder,
    private val logger: Logger
) {

    private val documentDao = db.webDavDocumentDao()

    suspend operator fun invoke(documentId: String, displayName: String): String? {
        logger.fine("WebDAV renameDocument $documentId $displayName")
        val doc = documentDao.get(documentId.toLong()) ?: throw FileNotFoundException()

        davClientBuilder
            .build(doc.mountId)
            .use { httpClient ->
                val hasOwnDisplayName = doc.displayName != null && doc.displayName != doc.name
                when {
                    // DAV:displayname is metadata, so changing it doesn't require a MOVE (RFC 4918 15.2)
                    hasOwnDisplayName && setDisplayName(httpClient, doc, displayName) ->
                        documentDao.update(doc.copy(displayName = displayName))
                    else ->
                        move(httpClient, doc, displayName)
                }
            }

        DocumentProviderUtils.notifyFolderChanged(context, doc.parentId)

        // document ID is unchanged; returning it would make the framework revoke its URI permissions
        return null
    }

    /**
     * Sets `DAV:displayname` of the given document.
     *
     * @return whether the server confirmed the change
     */
    private suspend fun setDisplayName(httpClient: HttpClient, doc: WebDavDocument, displayName: String): Boolean =
        try {
            DavResource(httpClient, doc.toKtorUrl(db))
                .proppatch(mapOf(WebDAV.DisplayName to displayName), emptyList())
                .allSuccessful()
        } catch (e: DavException) {
            logger.log(Level.FINE, "Couldn't set DAV:displayname, falling back to MOVE", e)
            false
        }

    private suspend fun move(httpClient: HttpClient, doc: WebDavDocument, displayName: String) {
        val oldUrl = doc.toKtorUrl(db)
        for (attempt in 0..DocumentProviderUtils.MAX_DISPLAYNAME_TO_MEMBERNAME_ATTEMPTS) {
            val newName = displayNameToMemberName(displayName, attempt)
            val newLocation = doc.copy(name = newName).toKtorUrl(db)
            try {
                val dav = DavResource(httpClient, oldUrl)
                dav.move(newLocation, false) {
                    // successfully renamed
                }
                // the server may keep the old DAV:displayname, but show the new name until the next PROPFIND
                documentDao.update(doc.copy(name = newName, displayName = null))
                return
            } catch (_: PreconditionFailedException) {
                // name already taken, try next one
            }
        }
    }

}
