/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.webdav.operation

import android.content.Context
import at.bitfire.dav4jvm.ktor.DavResource
import at.bitfire.dav4jvm.ktor.exception.HttpException
import at.bitfire.davdroid.db.AppDatabase
import at.bitfire.davdroid.webdav.DavHttpClientBuilder
import at.bitfire.davdroid.webdav.DocumentProviderUtils
import at.bitfire.davdroid.webdav.throwForDocumentProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.runBlocking
import java.io.FileNotFoundException
import java.util.logging.Logger
import javax.inject.Inject

class DeleteDocumentOperation @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: AppDatabase,
    private val davClientBuilder: DavHttpClientBuilder,
    private val logger: Logger
) {

    private val documentDao = db.webDavDocumentDao()

    operator fun invoke(documentId: String) = runBlocking {
        logger.fine("WebDAV removeDocument $documentId")
        val doc = documentDao.get(documentId.toLong()) ?: throw FileNotFoundException()

        try {
            davClientBuilder.build(doc.mountId).use { client ->
                val dav = DavResource(client, doc.toKtorUrl(db))
                dav.delete {
                    // successfully deleted
                }
            }
            logger.fine("Successfully removed")
            documentDao.delete(doc)

            DocumentProviderUtils.notifyFolderChanged(context, doc.parentId)
        } catch (e: HttpException) {
            e.throwForDocumentProvider(context)
        }
    }

}
