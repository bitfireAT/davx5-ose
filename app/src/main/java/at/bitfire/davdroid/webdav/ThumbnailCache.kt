package at.bitfire.davdroid.webdav

import android.content.Context
import android.graphics.Point
import android.os.storage.StorageManager
import androidx.core.content.ContextCompat
import at.bitfire.davdroid.log.Logger
import at.bitfire.davdroid.model.WebDavDocument
import java.io.File
import java.util.*

class ThumbnailCache(context: Context) {

    val cache: DiskCache

    init {
        val storageManager = ContextCompat.getSystemService(context, StorageManager::class.java)!!
        val cacheDir = File(context.cacheDir, "webdav/thumbnail")
        val maxBytes = storageManager.getCacheQuotaBytes(storageManager.getUuidForPath(cacheDir)) / 2
        Logger.log.info("Initializing WebDAV thumbnail cache with ${maxBytes/1024/1024} MB")

        cache = DiskCache(cacheDir, maxBytes)
    }


    fun get(doc: WebDavDocument, sizeHint: Point, generate: () -> ByteArray?): File? {
        val key = docToKey(doc, sizeHint)
        return cache.getFile(key, generate)
    }

    private fun docToKey(doc: WebDavDocument, sizeHint: Point): String {
        val segments = LinkedList<Any>()
        segments += doc.id
        segments += sizeHint.x
        segments += sizeHint.y

        when {
            doc.eTag != null -> {
                segments += "eTag"
                segments += doc.eTag!!
            }
            doc.lastModified != null -> {
                segments += "lastModified"
                segments += doc.lastModified!!
            }
            doc.size != null -> {
                segments += "size"
                segments += doc.size!!
            }
            else ->
                segments += UUID.randomUUID()
        }
        return CacheUtils.md5(*segments.toArray())
    }

}