/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.webdav.operation

import android.content.Context
import android.os.Build
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import at.bitfire.davdroid.db.AppDatabase
import at.bitfire.davdroid.di.IoDispatcher
import at.bitfire.davdroid.webdav.DavHttpClientBuilder
import at.bitfire.davdroid.webdav.DocumentProviderUtils
import at.bitfire.davdroid.webdav.HeadResponse
import at.bitfire.davdroid.webdav.RandomAccessCallbackWrapper
import at.bitfire.davdroid.webdav.StreamingFileDescriptor
import dagger.hilt.android.qualifiers.ApplicationContext
import io.ktor.client.HttpClient
import io.ktor.http.Url
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.FileNotFoundException
import java.util.logging.Logger
import javax.inject.Inject

class OpenDocumentOperation @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: AppDatabase,
    private val httpClientBuilder: DavHttpClientBuilder,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    private val logger: Logger,
    private val randomAccessCallbackWrapperFactory: RandomAccessCallbackWrapper.Factory,
    private val streamingFileDescriptorFactory: StreamingFileDescriptor.Factory
) {

    private val documentDao = db.webDavDocumentDao()

    operator fun invoke(documentId: String, mode: String, signal: CancellationSignal?): ParcelFileDescriptor = runBlocking {
        logger.fine("WebDAV openDocument $documentId $mode $signal")

        val doc = documentDao.get(documentId.toLong()) ?: throw FileNotFoundException()
        val url = doc.toUrl(db)
        val client = httpClientBuilder.buildKtor(doc.mountId, logBody = false)

        val readOnlyMode = when (mode) {
            "r" -> true
            "w", "wt" -> false
            else -> throw UnsupportedOperationException("Mode $mode not supported by WebDAV")
        }

        val accessScope = CoroutineScope(SupervisorJob())
        signal?.setOnCancelListener {
            logger.fine("Cancelling WebDAV access to $url")
            accessScope.cancel()
        }

        val fileInfo = accessScope.async {
            headRequest(client, url)
        }.await()
        logger.fine("Received file info: $fileInfo")

        // RandomAccessCallback.Wrapper / StreamingFileDescriptor are responsible for closing httpClient
        return@runBlocking if (
            androidSupportsRandomAccess &&
            readOnlyMode &&                     // WebDAV doesn't support random write access (natively)
            fileInfo.size != null &&            // file descriptor must return a useful value on getFileSize()
            (fileInfo.eTag != null || fileInfo.lastModified != null) &&     // we need a method to determine when the document changes during access
            fileInfo.supportsPartial == true    // WebDAV server must advertise random access
        ) {
            logger.fine("Creating RandomAccessCallback for $url")
            val accessor = randomAccessCallbackWrapperFactory.create(client, url, doc.mimeType, fileInfo, accessScope)
            accessor.fileDescriptor()

        } else {
            logger.fine("Creating StreamingFileDescriptor for $url")
            val fd = streamingFileDescriptorFactory.create(client, url, doc.mimeType, accessScope) { transferred, success ->
                // called when transfer is finished
                if (!success)
                    return@create

                val now = System.currentTimeMillis()
                if (!readOnlyMode /* write access */) {
                    // write access, update file size
                    documentDao.update(doc.copy(size = transferred, lastModified = now))
                }

                DocumentProviderUtils.notifyFolderChanged(context, doc.parentId)
            }

            if (readOnlyMode)
                fd.download()
            else
                fd.upload()
        }
    }

    private suspend fun headRequest(client: HttpClient, url: Url): HeadResponse = withContext(ioDispatcher) {
        HeadResponse.fromUrl(client, url)
    }


    companion object {

        /** openProxyFileDescriptor (required for random access) exists since Android 8.0 */
        val androidSupportsRandomAccess = Build.VERSION.SDK_INT >= 26

    }

}