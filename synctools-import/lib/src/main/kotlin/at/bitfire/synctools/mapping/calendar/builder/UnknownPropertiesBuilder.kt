/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.mapping.calendar.builder

import android.content.ContentValues
import android.content.Entity
import android.provider.CalendarContract.ExtendedProperties
import androidx.annotation.VisibleForTesting
import androidx.core.content.contentValuesOf
import at.bitfire.ical4android.UnknownProperty
import net.fortuna.ical4j.model.Property
import net.fortuna.ical4j.model.component.VEvent
import net.fortuna.ical4j.model.property.Color
import java.util.logging.Logger

class UnknownPropertiesBuilder: AndroidEntityBuilder {

    private val logger
        get() = Logger.getLogger(javaClass.name)

    override fun build(from: VEvent, main: VEvent, to: Entity) {
        for (property in unknownProperties(from)) {
            val values = buildUnknownProperty(property)
            if (values != null)
                to.addSubValue(ExtendedProperties.CONTENT_URI, values)
        }
    }

    private fun buildUnknownProperty(property: Property): ContentValues? {
        if (property.value == null) {
            logger.warning("Ignoring unknown property with null value")
            return null
        }
        if (property.value.length > UnknownProperty.MAX_UNKNOWN_PROPERTY_SIZE) {
            logger.warning("Ignoring unknown property with ${property.value.length} octets (too long)")
            return null
        }

        return contentValuesOf(
            ExtendedProperties.NAME to UnknownProperty.CONTENT_ITEM_TYPE,
            ExtendedProperties.VALUE to UnknownProperty.toJsonString(property)
        )
    }

    @VisibleForTesting
    internal fun unknownProperties(event: VEvent): List<Property> =
        event.propertyList.all.filterNot {
            KNOWN_PROPERTY_NAMES.contains(it.name.uppercase())
        }


    companion object {

        val KNOWN_PROPERTY_NAMES = arrayOf(
            // These properties are processed by their respective builders,
            // so we don't touch them in this builder.
            Property.UID,               // UidBuilder
            Property.RECURRENCE_ID,     // OriginalInstanceTimeBuilder
            Property.SEQUENCE,          // SequenceBuilder
            Property.SUMMARY,           // TitleBuilder
            Property.LOCATION,          // LocationBuilder
            Property.URL,               // UrlBuilder
            Property.DESCRIPTION,       // DescriptionBuilder
            Property.CATEGORIES,        // CategoriesBuilder
            Color.PROPERTY_NAME,        // ColorBuilder
            Property.DTSTART,           // StartTimeBuilder
            Property.DTEND,             // EndTimeBuilder
            Property.DURATION,          // - " -
            Property.RRULE,             // RecurrenceFieldsBuilder
            Property.RDATE,             // - " -
            Property.EXRULE,            // - " -
            Property.EXDATE,            // - " -
            Property.CLASS,             // AccessLevelBuilder
            Property.STATUS,            // StatusBuilder
            Property.TRANSP,            // AvailabilityBuilder
            Property.ORGANIZER,         // OrganizerBuilder
            Property.ATTENDEE,          // AttendeesBuilder

            // These properties can be ignored and shall not be saved as unknown properties.
            Property.DTSTAMP,
            Property.LAST_MODIFIED,
            Property.PRODID
        )

    }

}