/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.tasks

import net.fortuna.ical4j.model.Property
import net.fortuna.ical4j.model.PropertyList
import net.fortuna.ical4j.model.component.VToDo

object VToDoUtil {
    fun build(vararg properties: Property): VToDo {
        return VToDo(PropertyList(listOf(*properties)))
    }
}
