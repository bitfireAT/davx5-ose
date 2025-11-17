/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.db

import androidx.room.TypeConverter
import io.ktor.http.ContentType
import io.ktor.http.URLParserException
import io.ktor.http.Url
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

class Converters {

    @TypeConverter
    fun httpUrlToString(url: HttpUrl?) =
        url?.toString()

    @TypeConverter
    fun contentTypeToString(contentType: ContentType?) =
        contentType?.toString()

    @TypeConverter
    fun stringToHttpUrl(url: String?): HttpUrl? =
        url?.toHttpUrlOrNull()

    @TypeConverter
    fun stringToContentType(mimeType: String?): ContentType? =
        mimeType?.let {
            ContentType.parse(it)
        }

    @TypeConverter
    fun stringToUrl(url: String?): Url? =
        url?.let {
            try {
                Url(it)
            } catch (_: URLParserException) {
                null
            }
        }

    @TypeConverter
    fun urlToString(url: Url?) =
        url?.toString()

}