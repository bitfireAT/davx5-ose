/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.db

import androidx.room.TypeConverter
import at.bitfire.dav4jvm.ktor.toContentTypeOrNull
import at.bitfire.dav4jvm.ktor.toUrlOrNull
import at.bitfire.synctools.util.SensitiveString
import at.bitfire.synctools.util.SensitiveString.Companion.toSensitiveString
import io.ktor.http.ContentType
import io.ktor.http.Url

class Converters {

    @TypeConverter
    fun urlToString(url: Url?) =
        url?.toString()

    @TypeConverter
    fun contentTypeToString(contentType: ContentType?) =
        contentType?.toString()

    @TypeConverter
    fun sensitiveStringToString(value: SensitiveString?) =
        value?.asString()

    @TypeConverter
    fun stringToUrl(url: String?): Url? =
        url?.toUrlOrNull()

    @TypeConverter
    fun stringToContentType(mimeType: String?): ContentType? =
        mimeType?.toContentTypeOrNull()

    @TypeConverter
    fun stringToSensitiveString(value: String?): SensitiveString? =
        value?.toSensitiveString()

}