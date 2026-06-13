/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.jtx.builder

import android.content.ContentValues
import android.content.Entity
import androidx.core.content.contentValuesOf
import at.bitfire.ical4android.JtxICalObject
import at.bitfire.synctools.storage.UnknownProperty
import at.techbee.jtx.JtxContract
import net.fortuna.ical4j.model.Property
import net.fortuna.ical4j.model.component.CalendarComponent
import net.fortuna.ical4j.model.property.Color
import java.util.logging.Logger

class UnknownPropertiesBuilder : JtxObjectEntityBuilder {

    private val logger
        get() = Logger.getLogger(javaClass.name)

    override fun build(from: CalendarComponent, main: CalendarComponent, to: Entity) {
        for (property in unknownProperties(from)) {
            val values = buildUnknownProperty(property)
            if (values != null) {
                to.addSubValue(JtxContract.JtxUnknown.CONTENT_URI, values)
            }
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
            JtxContract.JtxUnknown.UNKNOWN_VALUE to UnknownProperty.toJsonString(property)
        )
    }

    private fun unknownProperties(component: CalendarComponent): List<Property> {
        return component.propertyList.all.filterNot { it.name.uppercase() in KNOWN_PROPERTY_NAMES }
    }


    companion object {

        val KNOWN_PROPERTY_NAMES = arrayOf(
            // These properties are processed by their respective builders, so we don't touch them in this builder.
            Property.ATTACH,
            Property.ATTENDEE,
            Property.CATEGORIES,
            Property.CLASS,
            Color.PROPERTY_NAME,
            Property.COMMENT,
            Property.COMPLETED,
            Property.CONTACT,
            Property.CREATED,
            Property.DESCRIPTION,
            Property.DTEND,
            Property.DTSTART,
            Property.DUE,
            Property.DURATION,
            Property.EXDATE,
            Property.GEO,
            Property.LAST_MODIFIED,
            Property.LOCATION,
            Property.ORGANIZER,
            Property.PERCENT_COMPLETE,
            Property.PRIORITY,
            Property.RECURRENCE_ID,
            Property.RELATED_TO,
            Property.RESOURCES,
            Property.RDATE,
            Property.RRULE,
            Property.SEQUENCE,
            Property.STATUS,
            Property.SUMMARY,
            Property.UID,
            Property.URL,
            JtxICalObject.X_PROP_COMPLETEDTIMEZONE,
            JtxICalObject.X_PROP_GEOFENCE_RADIUS,
            JtxICalObject.X_PROP_XSTATUS,

            // These properties can be ignored and shall not be saved as unknown properties.
            Property.DTSTAMP,
            Property.PRODID
        )
    }
}
