/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.jtx.builder

import android.content.Entity
import at.techbee.jtx.JtxContract
import net.fortuna.ical4j.model.Property
import net.fortuna.ical4j.model.component.CalendarComponent
import net.fortuna.ical4j.model.property.Status
import kotlin.jvm.optionals.getOrNull

class StatusBuilder : JtxObjectEntityBuilder {
    override fun build(from: CalendarComponent, main: CalendarComponent, to: Entity) {
        val status = main.getProperty<Status>(Property.STATUS).getOrNull()?.value
        to.entityValues.put(JtxContract.JtxICalObject.STATUS, status)
    }
}
