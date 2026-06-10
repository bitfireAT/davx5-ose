/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.jtx.builder

import android.content.Entity
import at.techbee.jtx.JtxContract
import net.fortuna.ical4j.model.Property
import net.fortuna.ical4j.model.component.CalendarComponent
import net.fortuna.ical4j.model.property.Uid
import kotlin.jvm.optionals.getOrNull

class UidBuilder : JtxObjectEntityBuilder {
    override fun build(from: CalendarComponent, main: CalendarComponent, to: Entity) {
        val uid = from.getProperty<Uid>(Property.UID).getOrNull()?.value
        to.entityValues.put(JtxContract.JtxICalObject.UID, uid)
    }
}
