package at.bitfire.davdroid.webdav

import android.content.Context
import android.os.storage.StorageManager
import androidx.core.content.ContextCompat
import at.bitfire.davdroid.AndroidSingleton
import at.bitfire.davdroid.log.Logger
import okhttp3.HttpUrl
import java.io.File

class PageCache(
    context: Context
) {

    companion object: AndroidSingleton<PageCache>() {
        override fun createInstance(context: Context) = PageCache(context)
    }

    data class Key(
        val url: HttpUrl,
        val state: DocumentState,
        val pageIndex: Long
    ) {
        fun asString() = CacheUtils.md5(url, state.asString(), pageIndex)
    }

    val cache: DiskCache


    init {
        val storageManager = ContextCompat.getSystemService(context, StorageManager::class.java)!!
        val cacheDir = File(context.cacheDir, "webdav/page")
        val maxBytes = storageManager.getCacheQuotaBytes(storageManager.getUuidForPath(cacheDir)) / 2
        Logger.log.info("Initializing WebDAV page cache in $cacheDir")

        cache = DiskCache(cacheDir, maxBytes)
    }

    fun get(key: Key, generate: () -> ByteArray?) =
        cache.get(key.asString(), generate)

}