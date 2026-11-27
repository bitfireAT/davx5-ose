/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.db

import android.annotation.SuppressLint
import android.os.Bundle
import android.provider.DocumentsContract.Document
import android.webkit.MimeTypeMap
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import at.bitfire.dav4jvm.ktor.toContentTypeOrNull
import at.bitfire.dav4jvm.ktor.withTrailingSlash
import at.bitfire.davdroid.util.DavUtils.MEDIA_TYPE_OCTET_STREAM
import at.bitfire.davdroid.webdav.DocumentState
import io.ktor.http.ContentType
import io.ktor.http.URLBuilder
import io.ktor.http.Url
import io.ktor.http.appendPathSegments
import java.io.FileNotFoundException
import java.time.Instant

@Entity(
    tableName = "webdav_document",
    foreignKeys = [
        ForeignKey(entity = WebDavMount::class, parentColumns = ["id"], childColumns = ["mountId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = WebDavDocument::class, parentColumns = ["id"], childColumns = ["parentId"], onDelete = ForeignKey.CASCADE)
    ],
    indices = [
        Index("mountId", "parentId", "name", unique = true),
        Index("parentId")
    ]
)
// If any column name is modified, also change it in [DavDocumentsProvider$queryChildDocuments]
data class WebDavDocument(

    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /** refers to the [WebDavMount] the document belongs to */
    val mountId: Long,

    /** refers to parent document (*null* when this document is a root document) */
    val parentId: Long?,

    /** file name (without any slashes) */
    val name: String,
    val isDirectory: Boolean = false,

    val displayName: String? = null,
    val mimeType: ContentType? = null,
    val eTag: String? = null,
    val lastModified: Long? = null,
    val size: Long? = null,

    val mayBind: Boolean? = null,
    val mayUnbind: Boolean? = null,
    val mayWriteContent: Boolean? = null,

    val quotaAvailable: Long? = null,
    val quotaUsed: Long? = null

) {

    fun cacheKey(): CacheKey? {
        if (eTag != null || lastModified != null)
            return CacheKey(id, DocumentState(eTag, lastModified?.let { ts -> Instant.ofEpochMilli(ts) }))
        return null
    }

    @SuppressLint("InlinedApi")
    fun toBundle(parent: WebDavDocument?): Bundle {
        if (parent?.isDirectory == false)
            throw IllegalArgumentException("Parent must be a directory")

        val bundle = Bundle().apply {
            putString(Document.COLUMN_DOCUMENT_ID, id.toString())
            putString(Document.COLUMN_DISPLAY_NAME, name)
        }

        displayName?.let { bundle.putString(Document.COLUMN_SUMMARY, it) }
        size?.let { bundle.putLong(Document.COLUMN_SIZE, it) }
        lastModified?.let { bundle.putLong(Document.COLUMN_LAST_MODIFIED, it) }

        // see RFC 3744 appendix B for required privileges for the various operations
        var flags = Document.FLAG_SUPPORTS_COPY
        if (isDirectory) {
            bundle.putString(Document.COLUMN_MIME_TYPE, Document.MIME_TYPE_DIR)
            if (mayBind != false)
                flags += Document.FLAG_DIR_SUPPORTS_CREATE
        } else {
            val reportedMimeType = mimeType ?:
                MimeTypeMap.getSingleton().getMimeTypeFromExtension(
                    MimeTypeMap.getFileExtensionFromUrl(name)
                )?.toContentTypeOrNull() ?:
                MEDIA_TYPE_OCTET_STREAM

            bundle.putString(Document.COLUMN_MIME_TYPE, reportedMimeType.toString())
            if (mimeType?.match(ContentType.Image.Any) == true)
                flags += Document.FLAG_SUPPORTS_THUMBNAIL
            if (mayWriteContent != false)
                flags += Document.FLAG_SUPPORTS_WRITE
        }
        if (parent?.mayUnbind != false)
            flags += Document.FLAG_SUPPORTS_DELETE or
                    Document.FLAG_SUPPORTS_MOVE or
                    Document.FLAG_SUPPORTS_RENAME
        bundle.putInt(Document.COLUMN_FLAGS, flags)

        return bundle
    }

    /**
     * Builds the URL of this document. Folders get a trailing slash (RFC 4918 §5.2).
     *
     * @return URL of this document
     * @throws FileNotFoundException if a parent document doesn't exist (anymore)
     */
    suspend fun toKtorUrl(db: AppDatabase): Url {
        val mount = db.webDavMountDao().getById(mountId)

        val segments = ArrayDeque<String>()
        var doc = this
        while (doc.parentId != null) {     // root document adds no segment
            segments.addFirst(doc.name)    // prepend segment
            doc = db.webDavDocumentDao().get(doc.parentId) ?: throw FileNotFoundException()
        }

        val url = URLBuilder(mount.url)
            .appendPathSegments(segments)
            .build()

        // folders get a trailing slash
        return if (isDirectory)
            url.withTrailingSlash()
        else
            url
    }


    /**
     * Represents a WebDAV document in a given state (with a given ETag/Last-Modified).
     */
    data class CacheKey(
        val docId: Long,
        val documentState: DocumentState
    )

}