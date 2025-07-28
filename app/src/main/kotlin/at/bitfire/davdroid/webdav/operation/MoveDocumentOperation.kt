/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.webdav.operation

import android.content.Context
import at.bitfire.dav4jvm.DavResource
import at.bitfire.dav4jvm.exception.HttpException
import at.bitfire.davdroid.db.AppDatabase
import at.bitfire.davdroid.di.IoDispatcher
import at.bitfire.davdroid.webdav.throwForDocumentProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.runInterruptible
import java.io.FileNotFoundException
import java.util.logging.Logger
import javax.inject.Inject

class MoveDocumentOperation @Inject constructor(
    private val actor: DavDocumentsActor,
    @ApplicationContext private val context: Context,
    private val db: AppDatabase,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    private val logger: Logger
) {

    private val documentDao = db.webDavDocumentDao()

    operator fun invoke(sourceDocumentId: String, sourceParentDocumentId: String, targetParentDocumentId: String): String = runBlocking {
        logger.fine("WebDAV moveDocument $sourceDocumentId $sourceParentDocumentId $targetParentDocumentId")
        val doc = documentDao.get(sourceDocumentId.toLong()) ?: throw FileNotFoundException()
        val dstParent = documentDao.get(targetParentDocumentId.toLong()) ?: throw FileNotFoundException()

        if (doc.mountId != dstParent.mountId)
            throw UnsupportedOperationException("Can't MOVE between WebDAV servers")

        val newLocation = dstParent.toHttpUrl(db).newBuilder()
            .addPathSegment(doc.name)
            .build()

        actor.httpClient(doc.mountId).use { client ->
            val dav = DavResource(client.okHttpClient, doc.toHttpUrl(db))
            try {
                runInterruptible(ioDispatcher) {
                    dav.move(newLocation, false) {
                        // successfully moved
                    }
                }

                documentDao.update(doc.copy(parentId = dstParent.id))

                actor.notifyFolderChanged(sourceParentDocumentId)
                actor.notifyFolderChanged(targetParentDocumentId)
            } catch (e: HttpException) {
                e.throwForDocumentProvider(context)
            }
        }

        doc.id.toString()
    }

}