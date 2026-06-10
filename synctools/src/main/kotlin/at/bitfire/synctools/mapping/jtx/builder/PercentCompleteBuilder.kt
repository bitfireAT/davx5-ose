/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.jtx.builder

import android.content.Entity
import at.techbee.jtx.JtxContract
import net.fortuna.ical4j.model.component.CalendarComponent
import net.fortuna.ical4j.model.component.VToDo
import net.fortuna.ical4j.model.property.PercentComplete
import kotlin.jvm.optionals.getOrNull

class PercentCompleteBuilder : JtxObjectEntityBuilder {
    override fun build(from: CalendarComponent, main: CalendarComponent, to: Entity) {

        if (from !is VToDo) {
            // VJOURNAL!=VToDO doesn't support the PERCENT_COMPLETE property
            return
        }

        val percentComplete = from.getProperty<PercentComplete>(PercentComplete.PERCENT_COMPLETE).getOrNull()?.percentage
        to.entityValues.put(JtxContract.JtxICalObject.PERCENT, percentComplete)
    }
}
