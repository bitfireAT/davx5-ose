/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.db

import androidx.room.TypeConverter
import at.bitfire.davdroid.util.SensitiveString
import at.bitfire.davdroid.util.SensitiveString.Companion.toSensitiveString
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull

class Converters {

    // HttpUrl <-> String

    @TypeConverter
    fun httpUrlToString(url: HttpUrl?) =
        url?.toString()

    @TypeConverter
    fun stringToHttpUrl(url: String?): HttpUrl? =
        url?.toHttpUrlOrNull()


    // MediaType <-> String

    @TypeConverter
    fun mediaTypeToString(mediaType: MediaType?) =
        mediaType?.toString()

    @TypeConverter
    fun stringToMediaType(mimeType: String?): MediaType? =
        mimeType?.toMediaTypeOrNull()


    // SensitiveString <-> String

    @TypeConverter
    fun sensitiveStringToString(sensitiveString: SensitiveString?): String? =
        sensitiveString?.asString()

    @TypeConverter
    fun stringToSensitiveString(string: String?): SensitiveString? =
        string?.toSensitiveString()

}