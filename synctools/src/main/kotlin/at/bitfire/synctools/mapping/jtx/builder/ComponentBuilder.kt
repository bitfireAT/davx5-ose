/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.jtx.builder

import android.content.Entity
import at.techbee.jtx.JtxContract
import net.fortuna.ical4j.model.component.CalendarComponent
import net.fortuna.ical4j.model.component.VJournal
import net.fortuna.ical4j.model.component.VToDo

class ComponentBuilder : JtxObjectEntityBuilder {
    override fun build(from: CalendarComponent, main: CalendarComponent, to: Entity) {
        val component = when (from) {
            is VToDo -> JtxContract.JtxICalObject.Component.VTODO.name
            is VJournal -> JtxContract.JtxICalObject.Component.VJOURNAL.name
            else -> error("Unsupported calendar component: ${from::class.simpleName}")
        }

        to.entityValues.put(JtxContract.JtxICalObject.COMPONENT, component)
    }
}
