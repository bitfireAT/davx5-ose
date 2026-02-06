/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.webdav.operation

import android.content.Context
import at.bitfire.dav4jvm.ktor.DavResource
import at.bitfire.dav4jvm.ktor.exception.HttpException
import at.bitfire.davdroid.db.AppDatabase
import at.bitfire.davdroid.di.qualifier.IoDispatcher
import at.bitfire.davdroid.webdav.DavHttpClientBuilder
import at.bitfire.davdroid.webdav.DocumentProviderUtils
import at.bitfire.davdroid.webdav.DocumentProviderUtils.displayNameToMemberName
import at.bitfire.davdroid.webdav.throwForDocumentProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import io.ktor.http.URLBuilder
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.runBlocking
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

    operator fun invoke(documentId: String, displayName: String): String? = runBlocking(ioDispatcher) {
        logger.fine("WebDAV renameDocument $documentId $displayName")
        val doc = documentDao.get(documentId.toLong()) ?: throw FileNotFoundException()

        httpClientBuilder
            .buildKtor(doc.mountId)
            .use { httpClient ->
                for (attempt in 0..DocumentProviderUtils.MAX_DISPLAYNAME_TO_MEMBERNAME_ATTEMPTS) {
                    val newName = displayNameToMemberName(displayName, attempt)
                    val oldUrl = doc.toKtorUrl(db)
                    val newLocation = URLBuilder(oldUrl)
                        .apply {
                        // Remove the last path segment (current file name) and add the new name
                        pathSegments = pathSegments.dropLast(1) + newName
                    }.build()
                    try {
                        val dav = DavResource(httpClient, oldUrl)
                        dav.move(newLocation, false) {
                            // successfully renamed
                        }
                        documentDao.update(doc.copy(name = newName))

                        DocumentProviderUtils.notifyFolderChanged(context, doc.parentId)

                        return@runBlocking doc.id.toString()
                    } catch (e: HttpException) {
                        e.throwForDocumentProvider(context, true)
                    }
                }
            }

        null
    }

}