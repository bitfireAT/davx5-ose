/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.webdav.operation

import android.content.Context
import android.os.Build
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.os.storage.StorageManager
import androidx.core.content.getSystemService
import at.bitfire.davdroid.db.AppDatabase
import at.bitfire.davdroid.di.IoDispatcher
import at.bitfire.davdroid.network.HttpClient
import at.bitfire.davdroid.webdav.DavHttpClientBuilder
import at.bitfire.davdroid.webdav.DocumentProviderUtils
import at.bitfire.davdroid.webdav.HeadResponse
import at.bitfire.davdroid.webdav.RandomAccessCallbackWrapper
import at.bitfire.davdroid.webdav.StreamingFileDescriptor
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.runInterruptible
import okhttp3.HttpUrl
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
    private val storageManager = context.getSystemService<StorageManager>()!!

    private suspend fun headRequest(client: HttpClient, url: HttpUrl): HeadResponse = runInterruptible(ioDispatcher) {
        HeadResponse.fromUrl(client, url)
    }

    operator fun invoke(documentId: String, mode: String, signal: CancellationSignal?): ParcelFileDescriptor = runBlocking {
        logger.fine("WebDAV openDocument $documentId $mode $signal")

        val doc = documentDao.get(documentId.toLong()) ?: throw FileNotFoundException()
        val url = doc.toHttpUrl(db)
        val client = httpClientBuilder.build(doc.mountId, logBody = false)

        val modeFlags = ParcelFileDescriptor.parseMode(mode)
        val readAccess = when (mode) {
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
            Build.VERSION.SDK_INT >= 26 &&      // openProxyFileDescriptor exists since Android 8.0
            readAccess &&                       // WebDAV doesn't support random write access natively
            fileInfo.size != null &&            // file descriptor must return a useful value on getFileSize()
            (fileInfo.eTag != null || fileInfo.lastModified != null) &&     // we need a method to determine whether the document has changed during access
            fileInfo.supportsPartial == true    // WebDAV server must support random access
        ) {
            logger.fine("Creating RandomAccessCallback for $url")
            val accessor = randomAccessCallbackWrapperFactory.create(client, url, doc.mimeType, fileInfo, accessScope)
            storageManager.openProxyFileDescriptor(modeFlags, accessor, accessor.workerHandler)
        } else {
            logger.fine("Creating StreamingFileDescriptor for $url")
            val fd = streamingFileDescriptorFactory.create(client, url, doc.mimeType, accessScope) { transferred ->
                // called when transfer is finished

                val now = System.currentTimeMillis()
                if (!readAccess /* write access */) {
                    // write access, update file size
                    documentDao.update(doc.copy(size = transferred, lastModified = now))
                }

                DocumentProviderUtils.notifyFolderChanged(context, doc.parentId)
            }

            if (readAccess)
                fd.download()
            else
                fd.upload()
        }
    }

}