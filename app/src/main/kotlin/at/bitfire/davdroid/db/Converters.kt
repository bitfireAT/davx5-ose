/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.db

import androidx.room.TypeConverter
import at.bitfire.dav4jvm.ktor.toContentTypeOrNull
import at.bitfire.dav4jvm.ktor.toUrlOrNull
import io.ktor.http.ContentType
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
        mimeType?.toContentTypeOrNull()

    @TypeConverter
    fun stringToUrl(url: String?): Url? =
        url?.toUrlOrNull()

    @TypeConverter
    fun urlToString(url: Url?) =
        url?.toString()

}