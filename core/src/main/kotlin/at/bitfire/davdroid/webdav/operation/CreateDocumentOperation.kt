/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.webdav.operation

import android.content.Context
import android.provider.DocumentsContract.Document
import at.bitfire.dav4jvm.ktor.DavResource
import at.bitfire.dav4jvm.ktor.exception.PreconditionFailedException
import at.bitfire.dav4jvm.ktor.toContentTypeOrNull
import at.bitfire.davdroid.db.AppDatabase
import at.bitfire.davdroid.db.WebDavDocument
import at.bitfire.davdroid.webdav.DavHttpClientBuilder
import at.bitfire.davdroid.webdav.DocumentProviderUtils
import at.bitfire.davdroid.webdav.DocumentProviderUtils.displayNameToMemberName
import dagger.hilt.android.qualifiers.ApplicationContext
import io.ktor.client.utils.EmptyContent
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import java.io.FileNotFoundException
import java.util.logging.Logger
import javax.inject.Inject

class CreateDocumentOperation @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: AppDatabase,
    private val davClientBuilder: DavHttpClientBuilder,
    private val logger: Logger
) {

    private val documentDao = db.webDavDocumentDao()

    suspend operator fun invoke(parentDocumentId: String, mimeType: String, displayName: String): String? {
        logger.fine("WebDAV createDocument $parentDocumentId $mimeType $displayName")
        val parent = documentDao.get(parentDocumentId.toLong()) ?: throw FileNotFoundException()
        val createDirectory = mimeType == Document.MIME_TYPE_DIR

        var docId: Long?
        davClientBuilder.build(parent.mountId).use { client ->
            for (attempt in 0..DocumentProviderUtils.MAX_DISPLAYNAME_TO_MEMBERNAME_ATTEMPTS) {
                val newDoc = WebDavDocument(
                    mountId = parent.mountId,
                    parentId = parent.id,
                    name = displayNameToMemberName(displayName, attempt),
                    isDirectory = createDirectory,
                    mimeType = mimeType.toContentTypeOrNull(),
                    size = if (createDirectory) null else 0
                )
                val doc = DavResource(client, newDoc.toKtorUrl(db))
                try {
                    if (createDirectory)
                        doc.mkCol(null) {
                            // directory successfully created
                        }
                    else
                        doc.put(
                            body = EmptyContent,
                            additionalHeaders = headersOf(HttpHeaders.IfNoneMatch, "*")
                        ) {
                            // document successfully created
                        }

                    docId = documentDao.insertOrReplace(newDoc)

                    DocumentProviderUtils.notifyFolderChanged(context, parentDocumentId)

                    return docId.toString()
                } catch (_: PreconditionFailedException) {
                    // name already taken, try next one
                }
            }
        }

        return null
    }

}
