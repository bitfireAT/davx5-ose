package at.bitfire.davdroid.model

import androidx.room.TypeConverter
import okhttp3.HttpUrl

class Converters {

    @TypeConverter
    fun httpUrlToString(url: HttpUrl?) =
            url?.toString()

    @TypeConverter
    fun stringToHttpUrl(url: String?): HttpUrl? =
            url?.let { HttpUrl.parse(it) }

}