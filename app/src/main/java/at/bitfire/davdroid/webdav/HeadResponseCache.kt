package at.bitfire.davdroid.webdav

import android.util.LruCache
import at.bitfire.davdroid.model.WebDavDocument
import java.util.*

object HeadResponseCache {

    const val MAX_SIZE = 10

    data class Key(
        val docId: Long,
        val documentState: DocumentState
    )

    val cache = LruCache<Key, HeadResponse>(MAX_SIZE)


    @Synchronized
    fun get(doc: WebDavDocument, generate: () -> HeadResponse): HeadResponse {
        var key: Key? = null
        if (doc.eTag != null || doc.lastModified != null) {
            key = Key(doc.id, DocumentState(doc.eTag, doc.lastModified?.let { ts -> Date(ts) }))
            cache.get(key)?.let { info ->
                return info
            }
        }

        val info = generate()
        if (key != null)
            cache.put(key, info)
        return info
    }

}