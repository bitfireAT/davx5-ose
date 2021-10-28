package at.bitfire.davdroid.model

import androidx.room.TypeConverter
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

}