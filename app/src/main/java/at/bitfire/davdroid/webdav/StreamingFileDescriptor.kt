package at.bitfire.davdroid.webdav

import android.content.Context
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import androidx.annotation.WorkerThread
import at.bitfire.dav4jvm.DavResource
import at.bitfire.dav4jvm.exception.HttpException
import at.bitfire.davdroid.DavUtils
import at.bitfire.davdroid.HttpClient
import at.bitfire.davdroid.log.Logger
import okhttp3.HttpUrl
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody
import okio.BufferedSink
import org.apache.commons.io.FileUtils
import java.io.IOException
import java.util.logging.Level
import kotlin.concurrent.thread

class StreamingFileDescriptor(
    val context: Context,
    val client: HttpClient,
    val url: HttpUrl,
    val mimeType: String?,
    val cancellationSignal: CancellationSignal?
) {

    companion object {
        /** 1 MB transfer buffer */
        private const val BUFFER_SIZE = FileUtils.ONE_MB.toInt()
    }


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
        DavResource(client.okHttpClient, url).get(mimeType ?: DavUtils.MIME_TYPE_ALL, null) { response ->
            response.body?.use { body ->
                if (response.isSuccessful) {
                    ParcelFileDescriptor.AutoCloseOutputStream(writeFd).use { output ->
                        val buffer = ByteArray(BUFFER_SIZE)
                        body.byteStream().use { source ->
                            var currentPage = 0

                            // read first chunk
                            var bytes = source.read(buffer)
                            var transferred = 0L
                            while (bytes != -1) {
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
            override fun contentType(): MediaType? = mimeType?.toMediaTypeOrNull()
            override fun isOneShot() = true
            override fun writeTo(sink: BufferedSink) {
                ParcelFileDescriptor.AutoCloseInputStream(readFd).use { input ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    var transferred = 0L

                    // read first chunk
                    var size = input.read(buffer, 0, BUFFER_SIZE)
                    while (size != -1) {
                        // write chunk
                        sink.write(buffer, 0, size)
                        transferred += size

                        // read next chunk
                        size = input.read(buffer, 0, BUFFER_SIZE)
                    }
                    Logger.log.finer("Uploaded $transferred byte(s) to $url")
                }
            }
        }
        DavResource(client.okHttpClient, url).put(body) {
            // upload successful
        }
    }

}