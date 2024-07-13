/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.webdav.cache

import android.content.Context
import android.graphics.Point
import android.os.Build
import android.os.storage.StorageManager
import android.text.format.Formatter
import androidx.core.content.getSystemService
import at.bitfire.davdroid.db.WebDavDocument
import at.bitfire.davdroid.log.Logger
import at.bitfire.davdroid.webdav.WebdavScoped
import com.google.common.hash.Hashing
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject

/**
 * Simple disk cache for image thumbnails.
 */
@WebdavScoped
class ThumbnailCache @Inject constructor(
    @ApplicationContext context: Context
) {

    val storage: DiskCache

    init {
        val storageManager = context.getSystemService<StorageManager>()!!
        val cacheDir = File(context.cacheDir, "webdav/thumbnail")
        val maxBytes = if (Build.VERSION.SDK_INT >= 26)
            storageManager.getCacheQuotaBytes(storageManager.getUuidForPath(cacheDir)) / 2
        else
            50 * 1024*1024  // 50 MB
        Logger.log.info("Initializing WebDAV thumbnail cache with ${Formatter.formatFileSize(context, maxBytes)}")

        storage = DiskCache(cacheDir, maxBytes)
    }


    fun get(docKey: WebDavDocument.CacheKey, sizeHint: Point, generate: () -> ByteArray?): File? {
        val key = Key(docKey, sizeHint)
        return storage.getFileOrPut(key.asString(), generate)
    }

    @Suppress("UnstableApiUsage")
    data class Key(
        val document: WebDavDocument.CacheKey,
        val size: Point
    ) {

        fun asString(): String {
            val hf = Hashing.sha256().newHasher()

            hf.putLong(document.docId)
            document.documentState.eTag?.let {
                hf.putString(it, Charsets.UTF_8)
            }
            document.documentState.lastModified?.let {
                hf.putLong(it.toEpochMilli())
            }
            hf.putInt(size.x)
            hf.putInt(size.y)

            return hf.hash().toString()
        }

    }

}