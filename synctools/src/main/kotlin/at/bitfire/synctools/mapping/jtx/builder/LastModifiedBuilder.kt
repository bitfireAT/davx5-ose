/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.jtx.builder

import android.content.Entity
import at.bitfire.synctools.icalendar.DatePropertyTzMapper.normalizedDate
import at.bitfire.synctools.util.AndroidTimeUtils.toTimestamp
import at.techbee.jtx.JtxContract
import net.fortuna.ical4j.model.Property
import net.fortuna.ical4j.model.component.CalendarComponent
import net.fortuna.ical4j.model.property.LastModified
import kotlin.jvm.optionals.getOrNull

class LastModifiedBuilder : JtxObjectEntityBuilder {
    override fun build(from: CalendarComponent, main: CalendarComponent, to: Entity) {
        val lastModified = from.getProperty<LastModified>(Property.LAST_MODIFIED).getOrNull()?.normalizedDate()?.toTimestamp()
        to.entityValues.put(JtxContract.JtxICalObject.LAST_MODIFIED, lastModified)
    }
}
