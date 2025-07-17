/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.webdav

import android.content.Context
import android.os.ProxyFileDescriptorCallback
import android.system.ErrnoException
import android.system.OsConstants
import android.text.format.Formatter
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import at.bitfire.dav4jvm.DavResource
import at.bitfire.dav4jvm.HttpUtils
import at.bitfire.dav4jvm.exception.DavException
import at.bitfire.dav4jvm.exception.HttpException
import at.bitfire.davdroid.R
import at.bitfire.davdroid.network.HttpClient
import at.bitfire.davdroid.ui.NotificationRegistry
import at.bitfire.davdroid.util.DavUtils
import com.google.common.cache.CacheBuilder
import com.google.common.cache.CacheLoader
import com.google.common.cache.LoadingCache
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.runInterruptible
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.MediaType
import java.io.InterruptedIOException
import java.net.HttpURLConnection
import java.util.logging.Logger

@RequiresApi(26)
class RandomAccessCallback @AssistedInject constructor(
    @Assisted val httpClient: HttpClient,
    @Assisted val url: HttpUrl,
    @Assisted val mimeType: MediaType?,
    @Assisted headResponse: HeadResponse,
    @Assisted private val externalScope: CoroutineScope,
    @ApplicationContext val context: Context,
    private val logger: Logger,
    private val notificationRegistry: NotificationRegistry
): ProxyFileDescriptorCallback() {

    companion object {

        /**
         * WebDAV resources will be read in chunks of this size (or less at the end of the file).
         */
        const val MAX_PAGE_SIZE = 2 * 1024*1024     // 2 MB

    }

    @AssistedFactory
    interface Factory {
        fun create(httpClient: HttpClient, url: HttpUrl, mimeType: MediaType?, headResponse: HeadResponse, externalScope: CoroutineScope): RandomAccessCallback
    }

    data class PageIdentifier(
        val offset: Long,
        val size: Int
    )

    private val dav = DavResource(httpClient.okHttpClient, url)

    private val fileSize = headResponse.size ?: throw IllegalArgumentException("Can only be used with given file size")
    private val documentState = headResponse.toDocumentState() ?: throw IllegalArgumentException("Can only be used with ETag/Last-Modified")

    private val notificationManager = NotificationManagerCompat.from(context)
    private val notification = NotificationCompat.Builder(context, notificationRegistry.CHANNEL_STATUS)
        .setPriority(NotificationCompat.PRIORITY_LOW)
        .setCategory(NotificationCompat.CATEGORY_STATUS)
        .setContentTitle(context.getString(R.string.webdav_notification_access))
        .setContentText(dav.fileName())
        .setSubText(Formatter.formatFileSize(context, fileSize))
        .setSmallIcon(R.drawable.ic_storage_notify)
        .setOngoing(true)
    private val notificationTag = url.toString()

    private val pageLoader = PageLoader(externalScope)
    private val pageCache: LoadingCache<PageIdentifier, ByteArray> = CacheBuilder.newBuilder()
        .maximumSize(10)    // don't cache more than 10 entries (MAX_PAGE_SIZE each)
        .softValues()       // use SoftReference for the page contents so they will be garbage collected if memory is needed
        .build(pageLoader)  // fetch actual content using pageLoader

    private val pagingReader = PagingReader(fileSize, MAX_PAGE_SIZE, pageCache)


    override fun onFsync() { /* not used */ }

    override fun onGetSize(): Long = runBlockingFd("onGetFileSize") {
        logger.fine("onGetFileSize $url")
        fileSize
    }

    override fun onRead(offset: Long, size: Int, data: ByteArray) = runBlockingFd("onRead") {
        logger.fine("onRead $url $offset $size")
        pagingReader.read(offset, size, data)
    }

    override fun onWrite(offset: Long, size: Int, data: ByteArray): Int {
        logger.fine("onWrite $url $offset $size")
        // ranged write requests not supported by WebDAV (yet)
        throw ErrnoException("onWrite", OsConstants.EROFS)
    }

    override fun onRelease() {
        logger.fine("onRelease")
        notificationManager.cancel(notificationTag, NotificationRegistry.NOTIFY_WEBDAV_ACCESS)
    }


    // scope / cancellation

    /**
     * Runs blocking in [externalScope].
     *
     * Exceptions (including [CancellationException]) are wrapped in an [ErrnoException], as expected by the file
     * descriptor / Storage Access Framework.
     *
     * @param functionName  name of the operation, passed to [ErrnoException] in case of cancellation
     */
    private fun<T> runBlockingFd(functionName: String, block: () -> T): T =
        runBlocking {
            try {
                externalScope.async {
                    block()
                }.await()
            } catch (e: CancellationException) {
                logger.warning("Random file access cancelled in $functionName, throwing ErrnoException(EINTR)")
                throw ErrnoException(functionName, OsConstants.EINTR, e)
            } catch (e: Throwable) {
                throw e.toErrNoException("onRead")
            }
        }

    private fun Throwable.toErrNoException(functionName: String) =
        ErrnoException(
            functionName,
            when (this) {
                is HttpException ->
                    when (code) {
                        HttpURLConnection.HTTP_FORBIDDEN -> OsConstants.EPERM
                        HttpURLConnection.HTTP_NOT_FOUND -> OsConstants.ENOENT
                        else -> OsConstants.EIO
                    }
                is IndexOutOfBoundsException -> OsConstants.ENXIO   // no such [device or] address, see man lseek (2)
                is InterruptedIOException -> OsConstants.EINTR
                is PartialContentNotSupportedException -> OsConstants.EOPNOTSUPP
                else -> OsConstants.EIO
            },
            this
        )


    /**
     * Responsible for loading (= downloading) a single page from the WebDAV resource.
     *
     * @param scope     cancellable scope the loader runs in (loader cancels I/O) when this scope is cancelled
     */
    inner class PageLoader(
        private val scope: CoroutineScope,
        private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
    ): CacheLoader<PageIdentifier, ByteArray>() {

        override fun load(key: PageIdentifier) = runBlocking {
            scope.async(ioDispatcher) {
                loadAsync(key)
            }.await()
        }

        private suspend fun loadAsync(key: PageIdentifier): ByteArray {
            val offset = key.offset
            val size = key.size
            logger.fine("Loading page $url $offset/$size")

            // update notification
            notificationRegistry.notifyIfPossible(NotificationRegistry.NOTIFY_WEBDAV_ACCESS, tag = notificationTag) {
                val progress =
                    if (fileSize == 0L)     // avoid division by zero
                        100
                    else
                        (offset * 100 / fileSize).toInt()
                notification.setProgress(100, progress, false).build()
            }

            val ifMatch: Headers =
                documentState.eTag?.let { eTag ->
                    Headers.headersOf("If-Match", "\"$eTag\"")
                } ?: documentState.lastModified?.let { lastModified ->
                    Headers.headersOf("If-Unmodified-Since", HttpUtils.formatDate(lastModified))
                } ?: throw DavException("ETag/Last-Modified required for random access")

            return runInterruptible {   // network I/O that should be cancelled by Thread interruption
                var result: ByteArray? = null
                dav.getRange(
                    DavUtils.acceptAnything(preferred = mimeType),
                    offset,
                    size,
                    ifMatch
                ) { response ->
                    if (response.code == 200)       // server doesn't support ranged requests
                        throw PartialContentNotSupportedException()
                    else if (response.code != 206)
                        throw HttpException(response)

                    result = response.body.bytes()
                }
                result ?: throw DavException("No response body")
            }
        }

    }


    class PartialContentNotSupportedException: Exception()

}