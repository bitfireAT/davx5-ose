/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.davdroid.webdav

import android.annotation.TargetApi
import android.app.ActivityManager
import android.content.Context
import android.os.CancellationSignal
import android.os.Handler
import android.os.HandlerThread
import android.os.ProxyFileDescriptorCallback
import android.system.ErrnoException
import android.system.OsConstants
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import at.bitfire.dav4jvm.DavResource
import at.bitfire.dav4jvm.HttpUtils
import at.bitfire.dav4jvm.exception.DavException
import at.bitfire.dav4jvm.exception.HttpException
import at.bitfire.davdroid.*
import at.bitfire.davdroid.log.Logger
import at.bitfire.davdroid.ui.NotificationUtils
import at.bitfire.davdroid.webdav.cache.MemoryCache
import at.bitfire.davdroid.webdav.cache.SegmentedCache
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.MediaType
import org.apache.commons.io.FileUtils
import java.io.InterruptedIOException
import java.lang.ref.WeakReference
import java.net.HttpURLConnection
import java.util.logging.Level

typealias MemorySegmentCache = MemoryCache<SegmentedCache.SegmentKey<RandomAccessCallback.DocumentKey>>

@TargetApi(26)
class RandomAccessCallback private constructor(
    val context: Context,
    val httpClient: HttpClient,
    val url: HttpUrl,
    val mimeType: MediaType?,
    val headResponse: HeadResponse,
    val cancellationSignal: CancellationSignal?
): ProxyFileDescriptorCallback(), SegmentedCache.PageLoader<RandomAccessCallback.DocumentKey> {

    companion object {
        /** one GET request per 2 MB */
        const val PAGE_SIZE: Int = (2*FileUtils.ONE_MB).toInt()

        private var _memoryCache: WeakReference<MemorySegmentCache>? = null

        @Synchronized
        fun getMemoryCache(context: Context): MemorySegmentCache {
            val cache = _memoryCache?.get()
            if (cache != null)
                return cache

            Logger.log.info("Creating memory cache")

            val maxHeapSizeMB = ContextCompat.getSystemService(context, ActivityManager::class.java)!!.memoryClass
            val cacheSize = maxHeapSizeMB * FileUtils.ONE_MB.toInt() / 2
            val newCache = MemorySegmentCache(cacheSize)

            _memoryCache = WeakReference(newCache)
            return newCache
        }

    }

    private val dav = DavResource(httpClient.okHttpClient, url)

    private val fileSize = headResponse.size ?: throw IllegalArgumentException("Can only be used with given file size")
    private val documentState = headResponse.toDocumentState() ?: throw IllegalArgumentException("Can only be used with ETag/Last-Modified")

    private val notificationManager = NotificationManagerCompat.from(context)
    private val notification = NotificationCompat.Builder(context, NotificationUtils.CHANNEL_STATUS)
        .setPriority(NotificationCompat.PRIORITY_LOW)
        .setCategory(NotificationCompat.CATEGORY_STATUS)
        .setContentTitle(context.getString(R.string.webdav_notification_access))
        .setContentText(dav.fileName())
        .setSubText(FileUtils.byteCountToDisplaySize(fileSize))
        .setSmallIcon(R.drawable.ic_storage_notify)
        .setOngoing(true)
    val notificationTag = url.toString()

    private val workerThread = HandlerThread(javaClass.simpleName).apply { start() }
    val workerHandler: Handler = Handler(workerThread.looper)

    val memoryCache = getMemoryCache(context)
    val cache = SegmentedCache(PAGE_SIZE, this, memoryCache)


    override fun onFsync() {
        Logger.log.fine("onFsync")
    }

    override fun onGetSize(): Long {
        Logger.log.fine("onGetFileSize $url")
        if (cancellationSignal?.isCanceled == true)
            throw ErrnoException("onGetFileSize", OsConstants.EINTR)

        return fileSize
    }

    override fun onRead(offset: Long, size: Int, data: ByteArray): Int {
        Logger.log.fine("onRead $url $offset $size")

        val progress =
            if (fileSize == 0L)     // avoid division by zero
                100
            else
                (offset*100/fileSize).toInt()
        notificationManager.notify(
            notificationTag,
            NotificationUtils.NOTIFY_WEBDAV_ACCESS,
            notification.setProgress(100, progress, false).build()
        )

        if (cancellationSignal?.isCanceled == true)
            throw ErrnoException("onRead", OsConstants.EINTR)

        try {
            val docKey = DocumentKey(url, documentState)
            return cache.read(docKey, offset, size, data)
        } catch (e: Exception) {
            Logger.log.log(Level.WARNING, "Couldn't read remote file", e)
            throw e.toErrNoException("onRead")
        }
    }

    override fun onWrite(offset: Long, size: Int, data: ByteArray): Int {
        // ranged write requests not supported by WebDAV (yet)
        throw ErrnoException("onWrite", OsConstants.EROFS)
    }

    override fun onRelease() {
        workerThread.quit()
        httpClient.close()

        notificationManager.cancel(notificationTag, NotificationUtils.NOTIFY_WEBDAV_ACCESS)
    }


    override fun load(key: SegmentedCache.SegmentKey<DocumentKey>, segmentSize: Int): ByteArray {
        if (key.documentKey.resource != url || key.documentKey.state != documentState)
            throw IllegalArgumentException()
        Logger.log.fine("Loading page $key")

        val ifMatch: Headers =
            documentState.eTag?.let { eTag ->
                Headers.headersOf("If-Match", "\"$eTag\"")
            } ?:
            documentState.lastModified?.let { lastModified ->
                Headers.headersOf("If-Unmodified-Since", HttpUtils.formatDate(lastModified))
            } ?: throw IllegalStateException("ETag/Last-Modified required for random access")

        var result: ByteArray? = null
        dav.getRange(
            mimeType?.toString() ?: DavUtils.MIME_TYPE_ACCEPT_ALL,
            key.segment * PAGE_SIZE.toLong(),
            PAGE_SIZE,
            ifMatch
        ) { response ->
            if (response.code != 206)
                throw DavException("Expected 206 Partial, got ${response.code} ${response.message}")

            result = response.body?.bytes()
        }

        return result ?: throw DavException("No response body")
    }


    private fun Exception.toErrNoException(functionName: String) =
        ErrnoException(functionName,
            when (this) {
                is HttpException ->
                    when (code) {
                        HttpURLConnection.HTTP_FORBIDDEN -> OsConstants.EPERM
                        HttpURLConnection.HTTP_NOT_FOUND -> OsConstants.ENOENT
                        else -> OsConstants.EIO
                    }
                is InterruptedIOException -> OsConstants.EINTR
                else -> OsConstants.EIO
            }
        )


    data class DocumentKey(
        val resource: HttpUrl,
        val state: DocumentState
    )


    /**
     * (2021/12/02) Currently Android's [StorageManager.openProxyFileDescriptor] has a memory leak:
     * the given callback is registered in [com.android.internal.os.AppFuseMount] (which adds it to
     * a [Map]), but is not unregistered anymore. So it stays in the memory until the whole mount
     * is unloaded. See https://issuetracker.google.com/issues/208788568
     *
     * Use this wrapper to ensure that all memory is released as soon as [onRelease] is called.
     */
    class Wrapper(
        context: Context,
        httpClient: HttpClient,
        url: HttpUrl,
        mimeType: MediaType?,
        headResponse: HeadResponse,
        cancellationSignal: CancellationSignal?
    ): ProxyFileDescriptorCallback() {

        var callback: RandomAccessCallback? = RandomAccessCallback(context, httpClient, url, mimeType, headResponse, cancellationSignal)

        override fun onFsync() =
            callback?.onFsync() ?: throw IllegalStateException("Must not be called after onRelease()")

        override fun onGetSize() =
            callback?.onGetSize() ?: throw IllegalStateException("Must not be called after onRelease()")

        override fun onRead(offset: Long, size: Int, data: ByteArray) =
            callback?.onRead(offset, size, data) ?: throw IllegalStateException("Must not be called after onRelease()")

        override fun onWrite(offset: Long, size: Int, data: ByteArray) =
            callback?.onWrite(offset, size, data) ?: throw IllegalStateException("Must not be called after onRelease()")

        override fun onRelease() {
            callback?.onRelease()
            callback = null
        }

    }

}