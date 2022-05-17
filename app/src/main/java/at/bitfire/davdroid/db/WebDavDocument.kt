/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.davdroid.db

import android.annotation.SuppressLint
import android.os.Bundle
import android.provider.DocumentsContract.Document
import android.webkit.MimeTypeMap
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import at.bitfire.davdroid.DavUtils.MEDIA_TYPE_OCTET_STREAM
import okhttp3.HttpUrl
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import java.io.FileNotFoundException

@Entity(
    tableName = "webdav_document",
    foreignKeys = [
        ForeignKey(entity = WebDavMount::class, parentColumns = ["id"], childColumns = ["mountId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = WebDavDocument::class, parentColumns = ["id"], childColumns = ["parentId"], onDelete = ForeignKey.CASCADE)
    ],
    indices = [
        Index("mountId", "parentId", "name", unique = true)
    ]
)
data class WebDavDocument(

    @PrimaryKey(autoGenerate = true)
    override var id: Long = 0,

    /** refers to the [WebDavMount] the document belongs to */
    val mountId: Long,

    /** refers to parent document (*null* when this document is a root document) */
    var parentId: Long?,

    /** file name (without any slashes) */
    var name: String,
    var isDirectory: Boolean = false,

    var displayName: String? = null,
    var mimeType: MediaType? = null,
    var eTag: String? = null,
    var lastModified: Long? = null,
    var size: Long? = null,

    var mayBind: Boolean? = null,
    var mayUnbind: Boolean? = null,
    var mayWriteContent: Boolean? = null,

    var quotaAvailable: Long? = null,
    var quotaUsed: Long? = null

): IdEntity {

    @SuppressLint("InlinedApi")
    fun toBundle(parent: WebDavDocument?): Bundle {
        if (parent?.isDirectory == false)
            throw IllegalArgumentException("Parent must be a directory")

        val bundle = Bundle()
        bundle.putString(Document.COLUMN_DOCUMENT_ID, id.toString())
        bundle.putString(Document.COLUMN_DISPLAY_NAME, name)

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
                )?.toMediaTypeOrNull() ?:
                MEDIA_TYPE_OCTET_STREAM

            bundle.putString(Document.COLUMN_MIME_TYPE, reportedMimeType.toString())
            if (mimeType?.type == "image")
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

    fun toHttpUrl(db: AppDatabase): HttpUrl {
        val mount = db.webDavMountDao().getById(mountId)

        val segments = mutableListOf(name)
        var parentIter = parentId
        while (parentIter != null) {
            val parent = db.webDavDocumentDao().get(parentIter) ?: throw FileNotFoundException()
            segments += parent.name
            parentIter = parent.parentId
        }

        val builder = mount.url.newBuilder()
        for (segment in segments.reversed())
            builder.addPathSegment(segment)
        return builder.build()
    }

}