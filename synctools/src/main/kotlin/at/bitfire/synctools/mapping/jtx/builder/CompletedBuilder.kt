/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.jtx.builder

import android.content.Entity
import at.bitfire.ical4android.JtxICalObject
import at.bitfire.synctools.icalendar.DatePropertyTzMapper.normalizedDate
import at.bitfire.synctools.util.AndroidTimeUtils.toTimestamp
import at.techbee.jtx.JtxContract
import net.fortuna.ical4j.model.component.CalendarComponent
import net.fortuna.ical4j.model.component.VToDo
import net.fortuna.ical4j.model.property.Completed
import net.fortuna.ical4j.model.property.XProperty
import java.util.logging.Logger
import kotlin.jvm.optionals.getOrNull

class CompletedBuilder : JtxObjectEntityBuilder {

    private val logger
        get() = Logger.getLogger(javaClass.name)

    override fun build(from: CalendarComponent, main: CalendarComponent, to: Entity) {
        if (from !is VToDo) {
            logger.warning("The Completed property is only supported for VTODO, this value is rejected.")
            return
        }

        val completed = from.getProperty<Completed>(Completed.COMPLETED).getOrNull()?.normalizedDate()?.toTimestamp()
        val completedTimezone = from.getProperty<XProperty>(JtxICalObject.X_PROP_COMPLETEDTIMEZONE).getOrNull()?.value

        to.entityValues.put(JtxContract.JtxICalObject.COMPLETED, completed)
        to.entityValues.put(JtxContract.JtxICalObject.COMPLETED_TIMEZONE, completedTimezone)
    }

}
