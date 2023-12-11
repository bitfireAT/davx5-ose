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
import androidx.core.content.getSystemService
import at.bitfire.dav4jvm.DavResource
import at.bitfire.dav4jvm.HttpUtils
import at.bitfire.dav4jvm.exception.DavException
import at.bitfire.dav4jvm.exception.HttpException
import at.bitfire.davdroid.*
import at.bitfire.davdroid.log.Logger
import at.bitfire.davdroid.network.HttpClient
import at.bitfire.davdroid.ui.NotificationUtils
import at.bitfire.davdroid.ui.NotificationUtils.notifyIfPossible
import at.bitfire.davdroid.util.DavUtils
import at.bitfire.davdroid.webdav.RandomAccessCallback.Wrapper.Companion.TIMEOUT_INTERVAL
import at.bitfire.davdroid.webdav.cache.MemoryCache
import at.bitfire.davdroid.webdav.cache.SegmentedCache
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.MediaType
import org.apache.commons.io.FileUtils
import ru.nsk.kstatemachine.Event
import ru.nsk.kstatemachine.State
import ru.nsk.kstatemachine.StateMachine
import ru.nsk.kstatemachine.createStdLibStateMachine
import ru.nsk.kstatemachine.finalState
import ru.nsk.kstatemachine.initialState
import ru.nsk.kstatemachine.onEntry
import ru.nsk.kstatemachine.onExit
import ru.nsk.kstatemachine.onFinished
import ru.nsk.kstatemachine.processEventBlocking
import ru.nsk.kstatemachine.state
import ru.nsk.kstatemachine.transitionOn
import java.io.InterruptedIOException
import java.lang.ref.WeakReference
import java.net.HttpURLConnection
import java.util.Timer
import java.util.TimerTask
import java.util.logging.Level
import kotlin.concurrent.schedule

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

            Logger.log.fine("Creating memory cache")
            val maxHeapSizeMB = context.getSystemService<ActivityManager>()!!.memoryClass
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

    val memoryCache = getMemoryCache(context)
    val cache = SegmentedCache(PAGE_SIZE, this, memoryCache)


    override fun onFsync() { /* not used */ }

    override fun onGetSize(): Long {
        Logger.log.fine("onGetFileSize $url")
        throwIfCancelled("onGetFileSize")
        return fileSize
    }

    override fun onRead(offset: Long, size: Int, data: ByteArray): Int {
        Logger.log.fine("onRead $url $offset $size")
        throwIfCancelled("onRead")

        val progress =
            if (fileSize == 0L)     // avoid division by zero
                100
            else
                (offset*100/fileSize).toInt()
        notificationManager.notifyIfPossible(
            notificationTag,
            NotificationUtils.NOTIFY_WEBDAV_ACCESS,
            notification.setProgress(100, progress, false).build()
        )

        try {
            val docKey = DocumentKey(url, documentState)
            return cache.read(docKey, offset, size, data)
        } catch (e: Exception) {
            Logger.log.log(Level.WARNING, "Couldn't read remote file", e)
            throw e.toErrNoException("onRead")
        }
    }

    override fun onWrite(offset: Long, size: Int, data: ByteArray): Int {
        Logger.log.fine("onWrite $url $offset $size")
        // ranged write requests not supported by WebDAV (yet)
        throw ErrnoException("onWrite", OsConstants.EROFS)
    }

    override fun onRelease() {
        Logger.log.fine("onRelease")
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
            DavUtils.acceptAnything(preferred = mimeType),
            key.segment * PAGE_SIZE.toLong(),
            PAGE_SIZE,
            ifMatch
        ) { response ->
            if (response.code == 200)       // server doesn't support ranged requests
                throw PartialContentNotSupportedException()
            else if (response.code != 206)
                throw HttpException(response)

            result = response.body?.bytes()
        }

        return result ?: throw DavException("No response body")
    }


    private fun throwIfCancelled(functionName: String) {
        if (cancellationSignal?.isCanceled == true) {
            Logger.log.warning("Random file access cancelled, throwing ErrnoException(EINTR)")
            throw ErrnoException(functionName, OsConstants.EINTR)
        }
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
                is PartialContentNotSupportedException -> OsConstants.EOPNOTSUPP
                else -> OsConstants.EIO
            }
        )


    data class DocumentKey(
        val resource: HttpUrl,
        val state: DocumentState
    )

    class PartialContentNotSupportedException: Exception()


    /**
     * (2021/12/02) Currently Android's [StorageManager.openProxyFileDescriptor] has a memory leak:
     * the given callback is registered in [com.android.internal.os.AppFuseMount] (which adds it to
     * a [Map]), but is not unregistered anymore. So it stays in the memory until the whole mount
     * is unloaded. See https://issuetracker.google.com/issues/208788568
     *
     * Use this wrapper to
     *
     * - ensure that all memory is released as soon as [onRelease] is called,
     * - provide timeout functionality: [RandomAccessCallback] will be closed when not
     * used for more than [TIMEOUT_INTERVAL] ms and re-created when necessary.
     *
     * @param httpClient    HTTP client – [Wrapper] is responsible to close it
     */
    class Wrapper(
        val context: Context,
        val httpClient: HttpClient,
        val url: HttpUrl,
        val mimeType: MediaType?,
        val headResponse: HeadResponse,
        val cancellationSignal: CancellationSignal?
    ): ProxyFileDescriptorCallback() {

        companion object {
            val TIMEOUT_INTERVAL = 15000L
        }

        sealed class Events {
            object Transfer : Event
            object NowIdle : Event
            object GoStandby : Event
            object Close : Event
        }
        /* We don't use a sealed class for states here because the states would then be singletons, while we can have
        multiple instances of the state machine (which require multiple instances of the states, too). */
        val machine = createStdLibStateMachine {
            lateinit var activeIdleState: State
            lateinit var activeTransferringState: State
            lateinit var standbyState: State
            lateinit var closedState: State

            initialState("active") {
                onEntry {
                    _callback = RandomAccessCallback(context, httpClient, url, mimeType, headResponse, cancellationSignal)
                }
                onExit {
                    _callback?.onRelease()
                    _callback = null
                }

                transitionOn<Events.GoStandby> { targetState = { standbyState } }
                transitionOn<Events.Close> { targetState = { closedState } }

                // active has two nested states: transferring (I/O running) and idle (starts timeout timer)
                activeIdleState = initialState("idle") {
                    val timer: Timer = Timer(true)
                    var timeout: TimerTask? = null

                    onEntry {
                        timeout = timer.schedule(TIMEOUT_INTERVAL) {
                            machine.processEventBlocking(Events.GoStandby)
                        }
                    }
                    onExit {
                        timeout?.cancel()
                        timeout = null
                    }
                    onFinished {
                        timer.cancel()
                    }

                    transitionOn<Events.Transfer> { targetState = { activeTransferringState } }
                }

                activeTransferringState = state("transferring") {
                    transitionOn<Events.NowIdle> { targetState = { activeIdleState } }
                }
            }

            standbyState = state("standby") {
                transitionOn<Events.Transfer> { targetState = { activeTransferringState } }
                transitionOn<Events.NowIdle> { targetState = { activeIdleState } }
                transitionOn<Events.Close> { targetState = { closedState } }
            }

            closedState = finalState("closed")
            onFinished {
                shutdown()
            }

            logger = StateMachine.Logger { message ->
                Logger.log.fine(message())
            }
        }

        private val workerThread = HandlerThread(javaClass.simpleName).apply { start() }
        val workerHandler: Handler = Handler(workerThread.looper)

        private var _callback: RandomAccessCallback? = null

        fun<T> requireCallback(block: (callback: RandomAccessCallback) -> T): T {
            machine.processEventBlocking(Events.Transfer)
            try {
                return block(_callback ?: throw IllegalStateException())
            } finally {
                machine.processEventBlocking(Events.NowIdle)
            }
        }


        /// states ///

        @Synchronized
        private fun shutdown() {
            httpClient.close()
            workerThread.quit()
        }


        /// delegating implementation of ProxyFileDescriptorCallback ///

        @Synchronized
        override fun onFsync() { /* not used */ }

        @Synchronized
        override fun onGetSize() =
            requireCallback { it.onGetSize() }

        @Synchronized
        override fun onRead(offset: Long, size: Int, data: ByteArray) =
            requireCallback { it.onRead(offset, size, data) }

        @Synchronized
        override fun onWrite(offset: Long, size: Int, data: ByteArray) =
            requireCallback { it.onWrite(offset, size, data) }

        @Synchronized
        override fun onRelease() {
            machine.processEventBlocking(Events.Close)
        }

    }

}