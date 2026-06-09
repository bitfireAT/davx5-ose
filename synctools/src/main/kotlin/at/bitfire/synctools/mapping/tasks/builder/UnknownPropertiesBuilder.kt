/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.tasks.builder

import android.content.Entity
import androidx.core.content.contentValuesOf
import at.bitfire.ical4android.UnknownProperty
import at.bitfire.synctools.storage.tasks.DmfsTaskList
import at.bitfire.synctools.storage.tasks.DmfsTasksContract.UNKNOWN_PROPERTY_DATA
import net.fortuna.ical4j.model.Property
import net.fortuna.ical4j.model.component.VToDo
import net.fortuna.ical4j.model.property.Color
import org.dmfs.tasks.contract.TaskContract.Properties
import java.util.logging.Logger

class UnknownPropertiesBuilder(
    private val taskList: DmfsTaskList
) : DmfsTaskEntityBuilder {

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
                taskList.tasksPropertiesUri(),
                contentValuesOf(
                    Properties.MIMETYPE to UnknownProperty.CONTENT_ITEM_TYPE,
                    UNKNOWN_PROPERTY_DATA to UnknownProperty.toJsonString(property)
                )
            )
        }
    }

    private fun unknownProperties(from: VToDo): List<Property> =
        from.propertyList.all.filterNot {
            KNOWN_PROPERTY_NAMES.contains(it.name.uppercase())
        }

    companion object {

        val KNOWN_PROPERTY_NAMES = arrayOf(
            // These properties are processed by their respective builders,
            // so we don't touch them in this builder.
            Property.UID,               // UidBuilder
            Property.SEQUENCE,          // SequenceBuilder
            Property.CREATED,           // CreatedBuilder
            Property.LAST_MODIFIED,     // LastModifiedBuilder
            Property.SUMMARY,           // TitleBuilder
            Property.LOCATION,          // LocationBuilder
            Property.GEO,               // GeoBuilder
            Property.DESCRIPTION,       // DescriptionBuilder
            Color.PROPERTY_NAME,        // ColorBuilder
            Property.URL,               // UrlBuilder
            Property.ORGANIZER,         // OrganizerBuilder
            Property.PRIORITY,          // PriorityBuilder
            Property.CLASS,             // ClassificationBuilder
            Property.STATUS,            // StatusBuilder
            Property.DUE,               // DueBuilder
            Property.DURATION,          // DurationBuilder
            Property.DTSTART,           // StartTimeBuilder / AllDayBuilder
            Property.COMPLETED,         // CompletedBuilder
            Property.PERCENT_COMPLETE,  // PercentCompleteBuilder
            Property.RRULE,             // RecurrenceFieldsBuilder
            Property.RDATE,             // RecurrenceFieldsBuilder
            // Note: EXRULE is intentionally absent – it's not mapped by RecurrenceFieldsBuilder
            // (the dmfs tasks contract has no EXRULE field), so it must be preserved here.
            Property.EXDATE,            // RecurrenceFieldsBuilder
            Property.CATEGORIES,        // CategoriesBuilder
            Property.COMMENT,           // CommentsBuilder
            Property.RELATED_TO,        // RelationsBuilder

            // These properties can be ignored and shall not be saved as unknown properties.
            Property.PRODID,
            Property.DTSTAMP,
        )

    }

}
