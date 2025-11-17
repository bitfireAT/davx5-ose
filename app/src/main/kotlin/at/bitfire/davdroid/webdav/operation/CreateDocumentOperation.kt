/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.webdav.operation

import android.content.Context
import android.provider.DocumentsContract.Document
import at.bitfire.dav4jvm.ktor.toContentTypeOrNull
import at.bitfire.dav4jvm.okhttp.DavResource
import at.bitfire.dav4jvm.okhttp.exception.HttpException
import at.bitfire.davdroid.db.AppDatabase
import at.bitfire.davdroid.db.WebDavDocument
import at.bitfire.davdroid.di.IoDispatcher
import at.bitfire.davdroid.webdav.DavHttpClientBuilder
import at.bitfire.davdroid.webdav.DocumentProviderUtils
import at.bitfire.davdroid.webdav.DocumentProviderUtils.displayNameToMemberName
import at.bitfire.davdroid.webdav.throwForDocumentProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.runInterruptible
import okhttp3.RequestBody
import java.io.FileNotFoundException
import java.util.logging.Logger
import javax.inject.Inject

class CreateDocumentOperation @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: AppDatabase,
    private val httpClientBuilder: DavHttpClientBuilder,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    private val logger: Logger
) {

    private val documentDao = db.webDavDocumentDao()

    operator fun invoke(parentDocumentId: String, mimeType: String, displayName: String): String? = runBlocking {
        logger.fine("WebDAV createDocument $parentDocumentId $mimeType $displayName")
        val parent = documentDao.get(parentDocumentId.toLong()) ?: throw FileNotFoundException()
        val createDirectory = mimeType == Document.MIME_TYPE_DIR

        var docId: Long?
        val client = httpClientBuilder.build(parent.mountId)
        for (attempt in 0..DocumentProviderUtils.MAX_DISPLAYNAME_TO_MEMBERNAME_ATTEMPTS) {
            val newName = displayNameToMemberName(displayName, attempt)
            val parentUrl = parent.toHttpUrl(db)
            val newLocation = parentUrl.newBuilder()
                .addPathSegment(newName)
                .build()
            val doc = DavResource(client, newLocation)
            try {
                runInterruptible(ioDispatcher) {
                    if (createDirectory)
                        doc.mkCol(null) {
                            // directory successfully created
                        }
                    else
                        doc.put(RequestBody.EMPTY, ifNoneMatch = true) {
                            // document successfully created
                        }
                }

                docId = documentDao.insertOrReplace(
                    WebDavDocument(
                        mountId = parent.mountId,
                        parentId = parent.id,
                        name = newName,
                        isDirectory = createDirectory,
                        mimeType = mimeType.toContentTypeOrNull(),
                        eTag = null,
                        lastModified = null,
                        size = if (createDirectory) null else 0
                    )
                )

                DocumentProviderUtils.notifyFolderChanged(context, parentDocumentId)

                return@runBlocking docId.toString()
            } catch (e: HttpException) {
                e.throwForDocumentProvider(context, ignorePreconditionFailed = true)
            }
        }

        null
    }

}