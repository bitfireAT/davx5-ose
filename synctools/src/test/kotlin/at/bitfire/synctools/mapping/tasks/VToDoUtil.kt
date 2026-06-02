/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.tasks

import net.fortuna.ical4j.model.ComponentList
import net.fortuna.ical4j.model.Property
import net.fortuna.ical4j.model.PropertyList
import net.fortuna.ical4j.model.component.VAlarm
import net.fortuna.ical4j.model.component.VToDo

object VToDoUtil {
    fun build(vararg properties: Property): VToDo {
        return VToDo(PropertyList(listOf(*properties)))
    }

    fun build(properties: List<Property>, alarms: List<VAlarm>): VToDo {
        return VToDo(PropertyList(properties), ComponentList(alarms))
    }
}
