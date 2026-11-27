/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.webdav.operation

import android.content.Context
import at.bitfire.dav4jvm.ktor.DavResource
import at.bitfire.dav4jvm.ktor.exception.PreconditionFailedException
import at.bitfire.davdroid.db.AppDatabase
import at.bitfire.davdroid.webdav.DavHttpClientBuilder
import at.bitfire.davdroid.webdav.DocumentProviderUtils
import at.bitfire.davdroid.webdav.DocumentProviderUtils.displayNameToMemberName
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.FileNotFoundException
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
                val oldUrl = doc.toKtorUrl(db)
                for (attempt in 0..DocumentProviderUtils.MAX_DISPLAYNAME_TO_MEMBERNAME_ATTEMPTS) {
                    val newName = displayNameToMemberName(displayName, attempt)
                    val newLocation = doc.copy(name = newName).toKtorUrl(db)
                    try {
                        val dav = DavResource(httpClient, oldUrl)
                        dav.move(newLocation, false) {
                            // successfully renamed
                        }
                        documentDao.update(doc.copy(name = newName))

                        DocumentProviderUtils.notifyFolderChanged(context, doc.parentId)

                        return doc.id.toString()
                    } catch (_: PreconditionFailedException) {
                        // name already taken, try next one
                    }
                }
            }

        return null
    }

}