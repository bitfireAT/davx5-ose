/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.davtasks.builder

import android.content.Entity
import androidx.core.content.contentValuesOf
import at.bitfire.synctools.mapping.UnknownProperty
import at.bitfire.synctools.storage.davtasks.DavTaskList
import at.bitfire.tasks.contract.TaskProperties
import net.fortuna.ical4j.model.Property
import net.fortuna.ical4j.model.component.VToDo
import net.fortuna.ical4j.model.property.Color
import java.util.logging.Logger

/**
 * RFC 5545 §3.8.8.1/.2 non-standard (X-) / IANA properties not otherwise mapped.
 *
 * There is deliberately no dedicated column for `EXRULE`: it is RFC 2445 legacy, removed by
 * RFC 5545, and preserved here rather than dropped (§0 of the design doc).
 */
class UnknownPropertiesBuilder(
    private val taskList: DavTaskList
) : DavTaskEntityBuilder {

    private val logger
        get() = Logger.getLogger(javaClass.name)

    override fun build(from: VToDo, to: Entity) {
        for (property in unknownProperties(from)) {
            val value = property.value
            if (value == null) {
                logger.warning("Ignoring unknown property with null value")
                continue
            }
            if (value.length > UnknownProperty.MAX_UNKNOWN_PROPERTY_SIZE) {
                logger.warning("Ignoring unknown property with ${value.length} octets (too long)")
                continue
            }

            to.addSubValue(
                taskList.tasksPropertiesUri(asSyncAdapter = false),
                contentValuesOf(
                    TaskProperties.MIMETYPE to TaskProperties.MIMETYPE_UNKNOWN_PROPERTY,
                    TaskProperties.DATA1 to UnknownProperty.toJsonString(property)
                )
            )
        }
    }

    private fun unknownProperties(from: VToDo): List<Property> =
        from.propertyList.all.filterNot { KNOWN_PROPERTY_NAMES.contains(it.name.uppercase()) }

    companion object {
        val KNOWN_PROPERTY_NAMES = arrayOf(
            Property.UID, Property.SEQUENCE, Property.CREATED, Property.LAST_MODIFIED,
            Property.SUMMARY, Property.LOCATION, Property.GEO, Property.DESCRIPTION,
            Color.PROPERTY_NAME, Property.URL, Property.ORGANIZER, Property.PRIORITY,
            Property.CLASS, Property.STATUS, Property.DUE, Property.DURATION, Property.DTSTART,
            Property.COMPLETED, Property.PERCENT_COMPLETE, Property.RRULE, Property.RDATE,
            // Note: EXRULE is intentionally absent — it has no dedicated column, so it must be preserved here
            Property.EXDATE, Property.CATEGORIES, Property.COMMENT, Property.RELATED_TO,
            Property.ATTENDEE, Property.ATTACH, Property.CONTACT, Property.RESOURCES,
            Property.REQUEST_STATUS,

            // ignored, not stored as unknown properties
            Property.PRODID, Property.DTSTAMP,
        )
    }

}
