/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.webdav.operation

import android.content.Context
import at.bitfire.dav4jvm.okhttp.DavResource
import at.bitfire.dav4jvm.okhttp.exception.HttpException
import at.bitfire.davdroid.db.AppDatabase
import at.bitfire.davdroid.di.IoDispatcher
import at.bitfire.davdroid.webdav.DavHttpClientBuilder
import at.bitfire.davdroid.webdav.DocumentProviderUtils
import at.bitfire.davdroid.webdav.DocumentProviderUtils.displayNameToMemberName
import at.bitfire.davdroid.webdav.throwForDocumentProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.runInterruptible
import java.io.FileNotFoundException
import java.util.logging.Logger
import javax.inject.Inject

class RenameDocumentOperation @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: AppDatabase,
    private val httpClientBuilder: DavHttpClientBuilder,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    private val logger: Logger
) {

    private val documentDao = db.webDavDocumentDao()

    operator fun invoke(documentId: String, displayName: String): String? = runBlocking {
        logger.fine("WebDAV renameDocument $documentId $displayName")
        val doc = documentDao.get(documentId.toLong()) ?: throw FileNotFoundException()

        val client = httpClientBuilder.build(doc.mountId)
        for (attempt in 0..DocumentProviderUtils.MAX_DISPLAYNAME_TO_MEMBERNAME_ATTEMPTS) {
            val newName = displayNameToMemberName(displayName, attempt)
            val oldUrl = doc.toHttpUrl(db)
            val newLocation = oldUrl.newBuilder()
                .removePathSegment(oldUrl.pathSegments.lastIndex)
                .addPathSegment(newName)
                .build()
            try {
                val dav = DavResource(client, oldUrl)
                runInterruptible(ioDispatcher) {
                    dav.move(newLocation, false) {
                        // successfully renamed
                    }
                }
                documentDao.update(doc.copy(name = newName))

                DocumentProviderUtils.notifyFolderChanged(context, doc.parentId)

                return@runBlocking doc.id.toString()
            } catch (e: HttpException) {
                e.throwForDocumentProvider(context, true)
            }
        }

        null
    }

}