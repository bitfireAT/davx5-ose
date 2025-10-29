/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.util

import okhttp3.HttpUrl
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import java.net.URI
import java.net.URISyntaxException
import java.util.Locale
import java.util.UUID

/**
 * Some WebDAV and HTTP network utility methods.
  */
object DavUtils {

    const val MIME_TYPE_ACCEPT_ALL = "*/*"

    val MEDIA_TYPE_JCARD = "application/vcard+json".toMediaType()
    val MEDIA_TYPE_OCTET_STREAM = "application/octet-stream".toMediaType()
    val MEDIA_TYPE_VCARD = "text/vcard".toMediaType()

    /**
     * Builds an HTTP `Accept` header that accepts anything (&#42;/&#42;), but optionally
     * specifies a preference.
     *
     * @param preferred  preferred MIME type (optional)
     *
     * @return `media-range` for `Accept` header that accepts anything, but prefers [preferred] (if it was specified)
     */
    fun acceptAnything(preferred: MediaType?): String =
        if (preferred != null)
            "$preferred, $MIME_TYPE_ACCEPT_ALL;q=0.8"
        else
            MIME_TYPE_ACCEPT_ALL

    @Suppress("FunctionName")
    fun ARGBtoCalDAVColor(colorWithAlpha: Int): String {
        val alpha = (colorWithAlpha shr 24) and 0xFF
        val color = colorWithAlpha and 0xFFFFFF
        return String.format(Locale.ROOT, "#%06X%02X", color, alpha)
    }

    /**
     * Generates a usable WebDAV resource name (file name) from an UID.
     *
     * If the UID contains only characters that are usually not problematic in file names,
     * the returned value is `<uid>.<suffix>`. If there are problematic characters,
     * the file name will be generated from a random UUID plus suffix instead.
     *
     * @param uid       UID of the iCalendar or vCard
     * @param suffix    suffix to use (without dot, for instance `ics` for iCalendar files)
     *
     * @return file name that can be used to upload the resource
     */
    fun fileNameFromUid(uid: String, suffix: String): String {
        val uidIsGoodBaseName: Boolean = uid.all { char ->
            // see RFC 2396 2.2
            char.isLetterOrDigit() || arrayOf(                  // allow letters and digits
                ';', ':', '@', '&', '=', '+', '$', ',',         // allow reserved characters except '/' and '?'
                '-', '_', '.', '!', '~', '*', '\'', '(', ')'    // allow unreserved characters
            ).contains(char)
        }
        val baseName = if (uidIsGoodBaseName) uid else UUID.randomUUID().toString()
        return "$baseName.$suffix"
    }

    data class UidGenerationResult(
        val uid: String,
        val uidIsGenerated: Boolean
    )
    /**
     * Generates a UID for an iCalendar/vCard if there is no existing UID.
     *
     * @param existingUid       existing UID (may be null)
     *
     * @return decomposable result that contains either the existing or the generated UID and whether it was generated
     */
    fun generateUidIfNecessary(existingUid: String?): UidGenerationResult =
        if (existingUid == null) {
            // generate new UID
            UidGenerationResult(UUID.randomUUID().toString(), uidIsGenerated = true)
        } else {
            // use existing UID
            UidGenerationResult(existingUid, uidIsGenerated = false)
        }


    // extension methods

    val HttpUrl.lastSegment: String
        get() = pathSegments.lastOrNull { it.isNotEmpty() } ?: "/"

    /**
     * Returns parent URL (parent folder). Always with trailing slash
     */
    fun HttpUrl.parent(): HttpUrl {
        if (pathSegments.size == 1 && pathSegments[0] == "")
            // already root URL
            return this

        val builder = newBuilder()

        if (pathSegments[pathSegments.lastIndex] == "") {
            // URL ends with a slash ("/some/thing/" -> ["some","thing",""]), remove two segments ("" at lastIndex and "thing" at lastIndex - 1)
            builder.removePathSegment(pathSegments.lastIndex)
            builder.removePathSegment(pathSegments.lastIndex - 1)
        } else
            // URL doesn't end with a slash ("/some/thing" -> ["some","thing"]), remove one segment ("thing" at lastIndex)
            builder.removePathSegment(pathSegments.lastIndex)

        // append trailing slash
        builder.addPathSegment("")

        return builder.build()
    }

    /**
     * Compares MIME type and subtype of two MediaTypes. Does _not_ compare parameters
     * like `charset` or `version`.
     *
     * @param other   MediaType to compare with
     *
     * @return *true* if type and subtype match; *false* if they don't
     */
    fun MediaType.sameTypeAs(other: MediaType) =
        type == other.type && subtype == other.subtype

    fun String.toURIorNull(): URI? = try {
        URI(this)
    } catch (_: URISyntaxException) {
        null
    }

}