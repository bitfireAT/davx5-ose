/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.jtx.builder

import android.content.Entity
import at.bitfire.synctools.icalendar.Css3Color
import at.techbee.jtx.JtxContract
import net.fortuna.ical4j.model.component.CalendarComponent
import net.fortuna.ical4j.model.property.Color
import kotlin.jvm.optionals.getOrNull

class ColorBuilder : JtxObjectEntityBuilder {
    override fun build(from: CalendarComponent, main: CalendarComponent, to: Entity) {
        val color = from.getProperty<Color>(Color.PROPERTY_NAME).getOrNull()
        to.entityValues.put(JtxContract.JtxICalObject.COLOR, color?.value?.let(Css3Color::colorFromString))
    }
}