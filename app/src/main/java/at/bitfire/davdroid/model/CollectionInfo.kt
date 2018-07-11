/*
 * Copyright © Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.davdroid.model

import android.content.ContentValues
import android.os.Parcel
import android.os.Parcelable
import at.bitfire.dav4android.Response
import at.bitfire.dav4android.UrlUtils
import at.bitfire.dav4android.property.*
import at.bitfire.davdroid.model.ServiceDB.Collections
import okhttp3.HttpUrl

/**
 * Represents a WebDAV collection.
 *
 * @constructor always appends a trailing slash to the URL
 */
data class CollectionInfo(

        /**
         * URL of the collection (including trailing slash)
         */
        val url: HttpUrl,

        var id: Long? = null,
        var serviceID: Long? = null,

        var type: Type? = null,

        var readOnly: Boolean = false,
        var forceReadOnly: Boolean = false,
        var displayName: String? = null,
        var description: String? = null,
        var color: Int? = null,

        var timeZone: String? = null,
        var supportsVEVENT: Boolean = false,
        var supportsVTODO: Boolean = false,
        var selected: Boolean = false,

        // subscriptions
        var source: String? = null,

        // non-persistent properties
        var confirmed: Boolean = false
): Parcelable {

    enum class Type {
        ADDRESS_BOOK,
        CALENDAR,
        WEBCAL          // iCalendar subscription
    }

    constructor(dav: Response): this(UrlUtils.withTrailingSlash(dav.href)) {
        dav[ResourceType::class.java]?.let { type ->
            when {
                type.types.contains(ResourceType.ADDRESSBOOK) -> this.type = Type.ADDRESS_BOOK
                type.types.contains(ResourceType.CALENDAR)    -> this.type = Type.CALENDAR
                type.types.contains(ResourceType.SUBSCRIBED)  -> this.type = Type.WEBCAL
            }
        }

        dav[CurrentUserPrivilegeSet::class.java]?.let { privilegeSet ->
            readOnly = !privilegeSet.mayWriteContent
        }

        dav[DisplayName::class.java]?.let {
            if (!it.displayName.isNullOrEmpty())
                displayName = it.displayName
        }

        when (type) {
            Type.ADDRESS_BOOK -> {
                dav[AddressbookDescription::class.java]?.let { description = it.description }
            }
            Type.CALENDAR, Type.WEBCAL -> {
                dav[CalendarDescription::class.java]?.let { description = it.description }
                dav[CalendarColor::class.java]?.let { color = it.color }
                dav[CalendarTimezone::class.java]?.let { timeZone = it.vTimeZone }

                if (type == Type.CALENDAR) {
                    supportsVEVENT = true
                    supportsVTODO = true
                    dav[SupportedCalendarComponentSet::class.java]?.let {
                        supportsVEVENT = it.supportsEvents
                        supportsVTODO = it.supportsTasks
                    }
                } else { // Type.WEBCAL
                    dav[Source::class.java]?.let { source = it.hrefs.firstOrNull() }
                    supportsVEVENT = true
                }
            }
        }
    }


    constructor(values: ContentValues): this(UrlUtils.withTrailingSlash(HttpUrl.parse(values.getAsString(Collections.URL))!!)) {
        id = values.getAsLong(Collections.ID)
        serviceID = values.getAsLong(Collections.SERVICE_ID)
        type = try {
            Type.valueOf(values.getAsString(Collections.TYPE))
        } catch (e: Exception) {
            null
        }

        readOnly = values.getAsInteger(Collections.READ_ONLY) != 0
        forceReadOnly = values.getAsInteger(Collections.FORCE_READ_ONLY) != 0
        displayName = values.getAsString(Collections.DISPLAY_NAME)
        description = values.getAsString(Collections.DESCRIPTION)

        color = values.getAsInteger(Collections.COLOR)

        timeZone = values.getAsString(Collections.TIME_ZONE)
        supportsVEVENT = getAsBooleanOrNull(values, Collections.SUPPORTS_VEVENT) ?: false
        supportsVTODO = getAsBooleanOrNull(values, Collections.SUPPORTS_VTODO) ?: false

        source = values.getAsString(Collections.SOURCE)

        selected = values.getAsInteger(Collections.SYNC) != 0
    }

    fun toDB(): ContentValues {
        val values = ContentValues()
        // Collections.SERVICE_ID is never changed
        type?.let { values.put(Collections.TYPE, it.name) }

        values.put(Collections.URL, url.toString())
        values.put(Collections.READ_ONLY, if (readOnly) 1 else 0)
        values.put(Collections.FORCE_READ_ONLY, if (forceReadOnly) 1 else 0)
        values.put(Collections.DISPLAY_NAME, displayName)
        values.put(Collections.DESCRIPTION, description)
        values.put(Collections.COLOR, color)

        values.put(Collections.TIME_ZONE, timeZone)
        values.put(Collections.SUPPORTS_VEVENT, if (supportsVEVENT) 1 else 0)
        values.put(Collections.SUPPORTS_VTODO, if (supportsVTODO) 1 else 0)

        values.put(Collections.SOURCE, source)

        values.put(Collections.SYNC, if (selected) 1 else 0)
        return values
    }


    private fun getAsBooleanOrNull(values: ContentValues, field: String): Boolean? {
        val i = values.getAsInteger(field)
        return if (i == null)
            null
        else
            (i != 0)
    }


    override fun describeContents(): Int = 0
    override fun writeToParcel(dest: Parcel, flags: Int) {
        fun<T> writeOrNull(value: T?, write: (T) -> Unit) {
            if (value == null)
                dest.writeByte(0)
            else {
                dest.writeByte(1)
                write(value)
            }
        }

        dest.writeString(url.toString())

        writeOrNull(id) { dest.writeLong(it) }
        writeOrNull(serviceID) { dest.writeLong(it) }

        dest.writeString(type?.name)

        dest.writeByte(if (readOnly) 1 else 0)
        dest.writeByte(if (forceReadOnly) 1 else 0)
        dest.writeString(displayName)
        dest.writeString(description)
        writeOrNull(color) { dest.writeInt(it) }

        dest.writeString(timeZone)
        dest.writeByte(if (supportsVEVENT) 1 else 0)
        dest.writeByte(if (supportsVTODO) 1 else 0)
        dest.writeByte(if (selected) 1 else 0)

        dest.writeString(source)

        dest.writeByte(if (confirmed) 1 else 0)
    }

    companion object CREATOR : Parcelable.Creator<CollectionInfo> {

        val DAV_PROPERTIES = arrayOf(
                ResourceType.NAME,
                CurrentUserPrivilegeSet.NAME,
                DisplayName.NAME,
                AddressbookDescription.NAME, SupportedAddressData.NAME,
                CalendarDescription.NAME, CalendarColor.NAME, SupportedCalendarComponentSet.NAME,
                Source.NAME
        )

        override fun createFromParcel(parcel: Parcel): CollectionInfo {
            fun<T> readOrNull(parcel: Parcel, read: () -> T): T? {
                return if (parcel.readByte() == 0.toByte())
                    null
                else
                    read()
            }

            return CollectionInfo(
                    HttpUrl.parse(parcel.readString())!!,

                    readOrNull(parcel) { parcel.readLong() },
                    readOrNull(parcel) { parcel.readLong() },

                    parcel.readString()?.let { Type.valueOf(it) },

                    parcel.readByte() != 0.toByte(),
                    parcel.readByte() != 0.toByte(),
                    parcel.readString(),
                    parcel.readString(),
                    readOrNull(parcel) { parcel.readInt() },

                    parcel.readString(),
                    parcel.readByte() != 0.toByte(),
                    parcel.readByte() != 0.toByte(),
                    parcel.readByte() != 0.toByte(),

                    parcel.readString(),

                    parcel.readByte() != 0.toByte()
            )
        }

        override fun newArray(size: Int) = arrayOfNulls<CollectionInfo>(size)

    }

}