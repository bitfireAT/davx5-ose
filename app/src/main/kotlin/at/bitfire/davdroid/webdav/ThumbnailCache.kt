/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.davdroid.webdav

import android.content.Context
import android.graphics.Point
import android.os.Build
import android.os.storage.StorageManager
import androidx.annotation.WorkerThread
import androidx.core.content.ContextCompat
import at.bitfire.davdroid.db.WebDavDocument
import at.bitfire.davdroid.log.Logger
import at.bitfire.davdroid.webdav.cache.CacheUtils
import at.bitfire.davdroid.webdav.cache.DiskCache
import org.apache.commons.io.FileUtils
import java.io.File
import java.util.*

@WorkerThread
class ThumbnailCache(context: Context) {

    val cache: DiskCache

    init {
        val storageManager = ContextCompat.getSystemService(context, StorageManager::class.java)!!
        val cacheDir = File(context.cacheDir, "webdav/thumbnail")
        val maxBytes = if (Build.VERSION.SDK_INT >= 26)
            storageManager.getCacheQuotaBytes(storageManager.getUuidForPath(cacheDir)) / 2
        else
            50*FileUtils.ONE_MB
        Logger.log.info("Initializing WebDAV thumbnail cache with ${FileUtils.byteCountToDisplaySize(maxBytes)}")

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