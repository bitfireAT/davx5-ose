/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.calendar.builder

import android.content.Entity
import android.provider.CalendarContract.Events
import android.provider.CalendarContract.ExtendedProperties
import androidx.core.content.contentValuesOf
import at.bitfire.ical4android.UnknownProperty
import net.fortuna.ical4j.model.component.VEvent
import net.fortuna.ical4j.model.property.Clazz

class AccessLevelBuilder : AndroidEventEntityBuilder {

    override fun build(from: VEvent, main: VEvent, to: Entity) {
        val accessLevel: Int
        val retainValue: Boolean

        val classification: Clazz? = from.classification
        when (classification?.value?.uppercase()) {
            Clazz.VALUE_PUBLIC -> {
                accessLevel = Events.ACCESS_PUBLIC
                retainValue = false
            }

            Clazz.VALUE_PRIVATE -> {
                accessLevel = Events.ACCESS_PRIVATE
                retainValue = false
            }

            Clazz.VALUE_CONFIDENTIAL -> {
                accessLevel = Events.ACCESS_CONFIDENTIAL
                retainValue = true
            }

            null -> {
                accessLevel = Events.ACCESS_DEFAULT
                retainValue = false
            }

            else -> {
                accessLevel = Events.ACCESS_PRIVATE
                retainValue = true
            }
        }

        // store access level in main row
        to.entityValues.put(Events.ACCESS_LEVEL, accessLevel)

        // add retained classification, if needed
        if (retainValue && classification != null)
            to.addSubValue(
                ExtendedProperties.CONTENT_URI,
                contentValuesOf(
                    ExtendedProperties.NAME to UnknownProperty.CONTENT_ITEM_TYPE,
                    ExtendedProperties.VALUE to UnknownProperty.toJsonString(classification)
                )
            )
    }

}