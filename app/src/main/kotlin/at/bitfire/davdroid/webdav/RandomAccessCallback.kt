/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.webdav

import android.annotation.TargetApi
import android.content.Context
import android.os.CancellationSignal
import android.os.ProxyFileDescriptorCallback
import android.system.ErrnoException
import android.system.OsConstants
import android.text.format.Formatter
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import at.bitfire.dav4jvm.DavResource
import at.bitfire.dav4jvm.HttpUtils
import at.bitfire.dav4jvm.exception.DavException
import at.bitfire.dav4jvm.exception.HttpException
import at.bitfire.davdroid.R
import at.bitfire.davdroid.network.HttpClient
import at.bitfire.davdroid.ui.NotificationUtils
import at.bitfire.davdroid.ui.NotificationUtils.notifyIfPossible
import at.bitfire.davdroid.util.DavUtils
import com.google.common.cache.CacheBuilder
import com.google.common.cache.CacheLoader
import com.google.common.cache.LoadingCache
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.runInterruptible
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.MediaType
import java.io.InterruptedIOException
import java.net.HttpURLConnection
import java.util.logging.Level
import java.util.logging.Logger

@TargetApi(26)
class RandomAccessCallback @AssistedInject constructor(
    @Assisted val httpClient: HttpClient,
    @Assisted val url: HttpUrl,
    @Assisted val mimeType: MediaType?,
    @Assisted headResponse: HeadResponse,
    @Assisted private val cancellationSignal: CancellationSignal?,
    @ApplicationContext val context: Context,
    private val logger: Logger
): ProxyFileDescriptorCallback() {

    companion object {

        /**
         * WebDAV resources will be read in chunks of this size (or less at the end of the file).
         */
        const val MAX_PAGE_SIZE = 2 * 1024*1024     // 2 MB

    }

    @AssistedFactory
    interface Factory {
        fun create(httpClient: HttpClient, url: HttpUrl, mimeType: MediaType?, headResponse: HeadResponse, cancellationSignal: CancellationSignal?): RandomAccessCallback
    }

    data class PageIdentifier(
        val offset: Long,
        val size: Int
    )

    private val dav = DavResource(httpClient.okHttpClient, url)

    private val fileSize = headResponse.size ?: throw IllegalArgumentException("Can only be used with given file size")
    private val documentState = headResponse.toDocumentState() ?: throw IllegalArgumentException("Can only be used with ETag/Last-Modified")

    private val notificationManager = NotificationManagerCompat.from(context)
    private val notification = NotificationCompat.Builder(context, NotificationUtils.CHANNEL_STATUS)
        .setPriority(NotificationCompat.PRIORITY_LOW)
        .setCategory(NotificationCompat.CATEGORY_STATUS)
        .setContentTitle(context.getString(R.string.webdav_notification_access))
        .setContentText(dav.fileName())
        .setSubText(Formatter.formatFileSize(context, fileSize))
        .setSmallIcon(R.drawable.ic_storage_notify)
        .setOngoing(true)
    private val notificationTag = url.toString()

    private val pageLoader = PageLoader()
    private val pageCache: LoadingCache<PageIdentifier, ByteArray> = CacheBuilder.newBuilder()
        .maximumSize(10)    // don't cache more than 10 entries (MAX_PAGE_SIZE each)
        .softValues()       // use SoftReference for the page contents so they will be garbage collected if memory is needed
        .build(pageLoader)  // fetch actual content using pageLoader

    private val pagingReader = PagingReader(fileSize, MAX_PAGE_SIZE, pageCache)

    init {
        cancellationSignal?.let {
            logger.fine("Cancelling random access to $url")
            pageLoader.cancelAll()
        }
    }


    override fun onFsync() { /* not used */ }

    override fun onGetSize(): Long {
        logger.fine("onGetFileSize $url")
        throwIfCancelled("onGetFileSize")
        return fileSize
    }

    override fun onRead(offset: Long, size: Int, data: ByteArray): Int {
        logger.fine("onRead $url $offset $size")
        throwIfCancelled("onRead")

        try {
            return pagingReader.read(offset, size, data)
        } catch (e: Exception) {
            logger.log(Level.WARNING, "Couldn't read from WebDAV resource", e)
            throw e.toErrNoException("onRead")
        }
    }

    override fun onWrite(offset: Long, size: Int, data: ByteArray): Int {
        logger.fine("onWrite $url $offset $size")
        // ranged write requests not supported by WebDAV (yet)
        throw ErrnoException("onWrite", OsConstants.EROFS)
    }

    override fun onRelease() {
        logger.fine("onRelease")
        notificationManager.cancel(notificationTag, NotificationUtils.NOTIFY_WEBDAV_ACCESS)
    }

    private fun throwIfCancelled(functionName: String) {
        if (cancellationSignal?.isCanceled == true) {
            logger.warning("Random file access cancelled, throwing ErrnoException(EINTR)")
            throw ErrnoException(functionName, OsConstants.EINTR)
        }
    }

    private fun Exception.toErrNoException(functionName: String) =
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
     */
    inner class PageLoader: CacheLoader<PageIdentifier, ByteArray>() {

        private val jobs = mutableSetOf<Deferred<ByteArray>>()

        fun cancelAll() {
            for (job in jobs)
                job.cancel()
        }

        override fun load(key: PageIdentifier): ByteArray {
            val offset = key.offset
            val size = key.size
            logger.fine("Loading page $url $offset/$size")

            // update notification
            val progress =
                if (fileSize == 0L)     // avoid division by zero
                    100
                else
                    (offset * 100 / fileSize).toInt()
            notificationManager.notifyIfPossible(
                notificationTag,
                NotificationUtils.NOTIFY_WEBDAV_ACCESS,
                notification.setProgress(100, progress, false).build()
            )

            // create async job that can be cancelled (and cancellation interrupts I/O)
            val job = CoroutineScope(Dispatchers.IO).async {
                runInterruptible {
                    val ifMatch: Headers =
                        documentState.eTag?.let { eTag ->
                            Headers.headersOf("If-Match", "\"$eTag\"")
                        } ?: documentState.lastModified?.let { lastModified ->
                            Headers.headersOf("If-Unmodified-Since", HttpUtils.formatDate(lastModified))
                        } ?: throw IllegalStateException("ETag/Last-Modified required for random access")

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

                        result = response.body?.bytes()
                    }
                    return@runInterruptible result ?: throw DavException("No response body")
                }
            }

            try {
                // register job in set so that it can be cancelled
                jobs += job

                // wait for result
                return runBlocking {
                    job.await()
                }
            } finally {
                jobs -= job
            }

        }

    }


    class PartialContentNotSupportedException: Exception()

}