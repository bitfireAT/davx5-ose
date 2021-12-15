/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.davdroid.webdav

import android.content.Context
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import androidx.annotation.WorkerThread
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import at.bitfire.dav4jvm.DavResource
import at.bitfire.dav4jvm.exception.HttpException
import at.bitfire.davdroid.DavUtils
import at.bitfire.davdroid.HttpClient
import at.bitfire.davdroid.R
import at.bitfire.davdroid.log.Logger
import at.bitfire.davdroid.ui.NotificationUtils
import okhttp3.HttpUrl
import okhttp3.MediaType
import okhttp3.RequestBody
import okhttp3.internal.headersContentLength
import okio.BufferedSink
import org.apache.commons.io.FileUtils
import java.io.IOException
import java.util.logging.Level
import kotlin.concurrent.thread

class StreamingFileDescriptor(
    val context: Context,
    val client: HttpClient,
    val url: HttpUrl,
    val mimeType: MediaType?,
    val cancellationSignal: CancellationSignal?,
    val finishedCallback: OnSuccessCallback
) {

    companion object {
        /** 1 MB transfer buffer */
        private const val BUFFER_SIZE = FileUtils.ONE_MB.toInt()
    }

    val dav = DavResource(client.okHttpClient, url)
    var transferred: Long = 0

    private val notificationManager = NotificationManagerCompat.from(context)
    private val notification = NotificationCompat.Builder(context, NotificationUtils.CHANNEL_STATUS)
        .setPriority(NotificationCompat.PRIORITY_LOW)
        .setCategory(NotificationCompat.CATEGORY_STATUS)
        .setContentText(dav.fileName())
        .setSmallIcon(R.drawable.ic_storage_notify)
        .setOngoing(true)
    val notificationTag = url.toString()


    fun download() = doStreaming(false)
    fun upload() = doStreaming(true)

    private fun doStreaming(upload: Boolean): ParcelFileDescriptor {
        val (readFd, writeFd) = ParcelFileDescriptor.createReliablePipe()

        val worker = thread {
            try {
                if (upload)
                    uploadNow(readFd)
                else
                    downloadNow(writeFd)
            } catch (e: HttpException) {
                Logger.log.log(Level.WARNING, "HTTP error when opening remote file", e)
                writeFd.closeWithError("${e.code} ${e.message}")
            } catch (e: Exception) {
                Logger.log.log(Level.INFO, "Couldn't serve file (not necessesarily an error)", e)
                writeFd.closeWithError(e.message)
            }

            try {
                readFd.close()
                writeFd.close()
            } catch (ignored: IOException) {}

            notificationManager.cancel(notificationTag, NotificationUtils.NOTIFY_WEBDAV_ACCESS)

            finishedCallback.onSuccess(transferred)
        }

        cancellationSignal?.setOnCancelListener {
            Logger.log.fine("Cancelling transfer of $url")
            worker.interrupt()
        }

        return if (upload)
            writeFd
        else
            readFd
    }

    @WorkerThread
    private fun downloadNow(writeFd: ParcelFileDescriptor) {
        dav.get(mimeType?.toString() ?: DavUtils.MIME_TYPE_ACCEPT_ALL, null) { response ->
            response.body?.use { body ->
                if (response.isSuccessful) {
                    val length = response.headersContentLength()

                    notification.setContentTitle(context.getString(R.string.webdav_notification_download))
                    if (length == -1L)
                        // unknown file size, show notification now (no updates on progress)
                        notificationManager.notify(
                            notificationTag,
                            NotificationUtils.NOTIFY_WEBDAV_ACCESS,
                            notification
                                .setProgress(100, 0, true)
                                .build()
                        )
                    else
                        // known file size
                        notification.setSubText(FileUtils.byteCountToDisplaySize(length))

                    ParcelFileDescriptor.AutoCloseOutputStream(writeFd).use { output ->
                        val buffer = ByteArray(BUFFER_SIZE)
                        body.byteStream().use { source ->
                            // read first chunk
                            var bytes = source.read(buffer)
                            while (bytes != -1) {
                                // update notification (if file size is known)
                                if (length != -1L)
                                    notificationManager.notify(
                                        notificationTag,
                                        NotificationUtils.NOTIFY_WEBDAV_ACCESS,
                                        notification
                                            .setProgress(100, (transferred*100/length).toInt(), false)
                                            .build()
                                    )

                                // write chunk
                                output.write(buffer, 0, bytes)
                                transferred += bytes

                                // read next chunk
                                bytes = source.read(buffer)
                            }
                            Logger.log.finer("Downloaded $transferred byte(s) from $url")
                        }
                    }

                } else
                    writeFd.closeWithError("${response.code} ${response.message}")
            }
        }
    }

    @WorkerThread
    private fun uploadNow(readFd: ParcelFileDescriptor) {
        val body = object: RequestBody() {
            override fun contentType(): MediaType? = mimeType
            override fun isOneShot() = true
            override fun writeTo(sink: BufferedSink) {
                notificationManager.notify(
                    notificationTag,
                    NotificationUtils.NOTIFY_WEBDAV_ACCESS,
                    notification
                        .setContentTitle(context.getString(R.string.webdav_notification_upload))
                        .build()
                )

                ParcelFileDescriptor.AutoCloseInputStream(readFd).use { input ->
                    val buffer = ByteArray(BUFFER_SIZE)

                    // read first chunk
                    var size = input.read(buffer)
                    while (size != -1) {
                        // write chunk
                        sink.write(buffer, 0, size)
                        transferred += size

                        // read next chunk
                        size = input.read(buffer)
                    }
                    Logger.log.finer("Uploaded $transferred byte(s) to $url")
                }
            }
        }
        DavResource(client.okHttpClient, url).put(body) {
            // upload successful
        }
    }


    fun interface OnSuccessCallback {
        fun onSuccess(transferred: Long)
    }

}