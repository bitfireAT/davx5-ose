/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.webdav.operation

import android.content.Context
import at.bitfire.dav4jvm.DavResource
import at.bitfire.dav4jvm.exception.HttpException
import at.bitfire.davdroid.db.AppDatabase
import at.bitfire.davdroid.db.WebDavDocument
import at.bitfire.davdroid.di.IoDispatcher
import at.bitfire.davdroid.webdav.DavHttpClientBuilder
import at.bitfire.davdroid.webdav.DocumentProviderUtils
import at.bitfire.davdroid.webdav.throwForDocumentProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.runInterruptible
import java.io.FileNotFoundException
import java.util.logging.Logger
import javax.inject.Inject

class CopyDocumentOperation @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: AppDatabase,
    private val httpClientBuilder: DavHttpClientBuilder,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    private val logger: Logger
) {

    private val documentDao = db.webDavDocumentDao()

    operator fun invoke(sourceDocumentId: String, targetParentDocumentId: String): String = runBlocking {
        logger.fine("WebDAV copyDocument $sourceDocumentId $targetParentDocumentId")
        val srcDoc = documentDao.get(sourceDocumentId.toLong()) ?: throw FileNotFoundException()
        val dstFolder = documentDao.get(targetParentDocumentId.toLong()) ?: throw FileNotFoundException()
        val name = srcDoc.name

        if (srcDoc.mountId != dstFolder.mountId)
            throw UnsupportedOperationException("Can't COPY between WebDAV servers")

        httpClientBuilder.build(srcDoc.mountId).use { client ->
            val dav = DavResource(client.okHttpClient, srcDoc.toHttpUrl(db))
            val dstUrl = dstFolder.toHttpUrl(db).newBuilder()
                .addPathSegment(name)
                .build()

            try {
                runInterruptible(ioDispatcher) {
                    dav.copy(dstUrl, false) {
                        // successfully copied
                    }
                }
            } catch (e: HttpException) {
                e.throwForDocumentProvider(context)
            }

            val dstDocId = documentDao.insertOrReplace(
                WebDavDocument(
                    mountId = dstFolder.mountId,
                    parentId = dstFolder.id,
                    name = name,
                    isDirectory = srcDoc.isDirectory,
                    displayName = srcDoc.displayName,
                    mimeType = srcDoc.mimeType,
                    size = srcDoc.size
                )
            ).toString()

            DocumentProviderUtils.notifyFolderChanged(context, targetParentDocumentId)

            /* return */ dstDocId
        }
    }

}