/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.jtx.handler

import android.content.Entity
import at.bitfire.synctools.icalendar.plusAssign
import at.techbee.jtx.JtxContract
import net.fortuna.ical4j.model.component.CalendarComponent
import net.fortuna.ical4j.model.property.LastModified
import java.time.Instant

class LastModifiedHandler : JtxObjectEntityHandler {
    override fun process(from: Entity, main: Entity, to: CalendarComponent) {
        from.entityValues.getAsLong(JtxContract.JtxICalObject.LAST_MODIFIED)?.let { lastModified ->
            val instant = Instant.ofEpochMilli(lastModified)
            to += LastModified(instant)
        }
    }
}
