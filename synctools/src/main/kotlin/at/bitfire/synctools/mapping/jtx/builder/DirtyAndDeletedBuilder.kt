/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.jtx.builder

import android.content.Entity
import at.techbee.jtx.JtxContract
import net.fortuna.ical4j.model.component.CalendarComponent

class DirtyAndDeletedBuilder : JtxObjectEntityBuilder {
    override fun build(from: CalendarComponent, main: CalendarComponent, to: Entity) {
        // DIRTY and DELETED is always unset when we create or update an event row
        to.entityValues.put(JtxContract.JtxICalObject.DIRTY, false)
        to.entityValues.put(JtxContract.JtxICalObject.DELETED, false)
    }
}
