/*
 * Copyright © Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.davdroid.model

import android.content.ContentValues
import at.bitfire.dav4android.DavResource
import at.bitfire.dav4android.property.*
import at.bitfire.davdroid.model.ServiceDB.Collections
import java.io.Serializable

data class CollectionInfo @JvmOverloads constructor(
        val url: String,

        var id: Long? = null,
        var serviceID: Long? = null,

        var type: Type? = null,

        var readOnly: Boolean = false,
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
): Serializable {

    enum class Type {
        ADDRESS_BOOK,
        CALENDAR,
        WEBCAL          // iCalendar subscription
    }

    companion object {

        @JvmField
        val DAV_PROPERTIES = arrayOf(
            ResourceType.NAME,
            CurrentUserPrivilegeSet.NAME,
            DisplayName.NAME,
            AddressbookDescription.NAME, SupportedAddressData.NAME,
            CalendarDescription.NAME, CalendarColor.NAME, SupportedCalendarComponentSet.NAME,
            Source.NAME
        )

    }


    constructor(dav: DavResource): this(dav.location.toString()) {
        (dav.properties[ResourceType.NAME] as ResourceType?)?.let { type ->
            when {
                type.types.contains(ResourceType.ADDRESSBOOK) -> this.type = Type.ADDRESS_BOOK
                type.types.contains(ResourceType.CALENDAR)    -> this.type = Type.CALENDAR
                type.types.contains(ResourceType.SUBSCRIBED)  -> this.type = Type.WEBCAL
            }
        }

        (dav.properties[CurrentUserPrivilegeSet.NAME] as CurrentUserPrivilegeSet?)?.let { privilegeSet ->
            readOnly = !privilegeSet.mayWriteContent
        }

        (dav.properties[DisplayName.NAME] as DisplayName?)?.let {
            if (!it.displayName.isNullOrEmpty())
                displayName = it.displayName
        }

        when (type) {
            Type.ADDRESS_BOOK -> {
                (dav.properties[AddressbookDescription.NAME] as AddressbookDescription?)?.let { description = it.description }
            }
            Type.CALENDAR -> {
                (dav.properties[CalendarDescription.NAME] as CalendarDescription?)?.let { description = it.description }
                (dav.properties[CalendarColor.NAME] as CalendarColor?)?.let { color = it.color }
                (dav.properties[CalendarTimezone.NAME] as CalendarTimezone?)?.let { timeZone = it.vTimeZone }

                supportsVEVENT = true
                supportsVTODO = true
                (dav.properties[SupportedCalendarComponentSet.NAME] as SupportedCalendarComponentSet?)?.let {
                    supportsVEVENT = it.supportsEvents
                    supportsVTODO = it.supportsTasks
                }
            }
            Type.WEBCAL -> {
                (dav.properties[Source.NAME] as Source?)?.let { source = it.hrefs.firstOrNull() }
                supportsVEVENT = true
            }
        }
    }


    constructor(values: ContentValues): this(values.getAsString(Collections.URL)) {
        id = values.getAsLong(Collections.ID)
        serviceID = values.getAsLong(Collections.SERVICE_ID)
        type = try {
            Type.valueOf(values.getAsString(Collections.TYPE))
        } catch (e: Exception) {
            null
        }

        readOnly = values.getAsInteger(Collections.READ_ONLY) != 0
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

        values.put(Collections.URL, url)
        values.put(Collections.READ_ONLY, if (readOnly) 1 else 0)
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

}