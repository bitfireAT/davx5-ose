/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.db

import androidx.room.TypeConverter
import io.ktor.http.URLParserException
import io.ktor.http.Url
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull

class Converters {

    @TypeConverter
    fun httpUrlToString(url: HttpUrl?) =
        url?.toString()

    @TypeConverter
    fun mediaTypeToString(mediaType: MediaType?) =
        mediaType?.toString()

    @TypeConverter
    fun stringToHttpUrl(url: String?): HttpUrl? =
        url?.toHttpUrlOrNull()

    @TypeConverter
    fun stringToMediaType(mimeType: String?): MediaType? =
        mimeType?.toMediaTypeOrNull()

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