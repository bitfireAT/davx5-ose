/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.jtx.builder

import android.content.Entity
import at.techbee.jtx.JtxContract
import net.fortuna.ical4j.model.component.CalendarComponent
import net.fortuna.ical4j.model.property.Priority
import kotlin.jvm.optionals.getOrNull

class PriorityBuilder : JtxObjectEntityBuilder {
    override fun build(from: CalendarComponent, main: CalendarComponent, to: Entity) {
        val priority = from.getProperty<Priority>(Priority.PRIORITY).getOrNull()?.level
        to.entityValues.put(JtxContract.JtxICalObject.PRIORITY, priority)
    }
}
