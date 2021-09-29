package at.bitfire.davdroid.webdav

import android.content.Context
import android.os.CancellationSignal
import android.os.Handler
import android.os.HandlerThread
import android.os.ProxyFileDescriptorCallback
import android.system.ErrnoException
import android.system.OsConstants
import at.bitfire.dav4jvm.DavResource
import at.bitfire.dav4jvm.HttpUtils
import at.bitfire.dav4jvm.exception.HttpException
import at.bitfire.davdroid.DavUtils
import at.bitfire.davdroid.HttpClient
import at.bitfire.davdroid.log.Logger
import okhttp3.Headers
import okhttp3.HttpUrl
import org.apache.commons.io.IOUtils
import java.io.InterruptedIOException
import java.net.HttpURLConnection
import java.util.logging.Level

class RandomAccessCallback(
    val context: Context,
    val httpClient: HttpClient,
    val url: HttpUrl,
    val mimeType: String?,
    val headResponse: HeadResponse,
    val cancellationSignal: CancellationSignal?
): ProxyFileDescriptorCallback(), RandomAccessBuffer.Reader {

    private val dav = DavResource(httpClient.okHttpClient, url)

    private val fileSize = headResponse.size ?: throw IllegalArgumentException("Can only be used with given file size")
    private val documentState = headResponse.toDocumentState() ?: throw IllegalArgumentException("Can only be used with ETag/Last-Modified")

    private val workerThread = HandlerThread(javaClass.name).apply { start() }
    val workerHandler: Handler = Handler(workerThread.looper)

    val buffer: RandomAccessBuffer = RandomAccessBuffer(context, url, fileSize, documentState, this)


    override fun onFsync() {
    }

    override fun onGetSize(): Long {
        Logger.log.fine("onGetFileSize $url")
        if (cancellationSignal?.isCanceled == true)
            throw ErrnoException("onGetFileSize", OsConstants.EINTR)

        return fileSize
    }


    override fun onRead(offset: Long, size: Int, data: ByteArray): Int {
        Logger.log.fine("onRead $url $offset $size")
        if (cancellationSignal?.isCanceled == true)
            throw ErrnoException("onRead", OsConstants.EINTR)

        return buffer.read(offset, size, data)
    }

    override fun readDirect(offset: Long, size: Int, dst: ByteArray): Int {
        Logger.log.fine("readDirect $offset $size")
        try {
            val ifMatch: Headers =
                headResponse.eTag?.let { eTag ->
                    Headers.headersOf("If-Match", "\"$eTag\"")
                } ?:
                headResponse.lastModified?.let { lastModified ->
                    Headers.headersOf("If-Unmodified-Since", HttpUtils.formatDate(lastModified))
                } ?: throw IllegalStateException("ETag/Last-Modified required for random access")
            dav.getRange(mimeType ?: DavUtils.MIME_TYPE_ALL, offset, size, ifMatch) { response ->
                if (response.code == HttpURLConnection.HTTP_PRECON_FAILED)
                    // file has been modified while reading
                    throw ErrnoException("onRead", OsConstants.EBADF)
                if (response.code != 206)
                    // range request not successful
                    throw ErrnoException("onRead", OsConstants.ENOTSUP)

                response.body?.byteStream().use { stream ->
                    IOUtils.read(stream, dst, 0, size)
                }
            }

            return size
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
        httpClient.close()
        workerThread.quitSafely()
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

}