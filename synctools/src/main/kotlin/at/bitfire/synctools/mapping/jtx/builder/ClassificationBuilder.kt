/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.jtx.builder

import android.content.Entity
import at.techbee.jtx.JtxContract
import net.fortuna.ical4j.model.component.CalendarComponent
import net.fortuna.ical4j.model.property.Clazz
import kotlin.jvm.optionals.getOrNull

class ClassificationBuilder : JtxObjectEntityBuilder {
    override fun build(from: CalendarComponent, main: CalendarComponent, to: Entity) {
        val classification = from.getProperty<Clazz>(Clazz.CLASS).getOrNull()?.value
        to.entityValues.put(JtxContract.JtxICalObject.CLASSIFICATION, classification)
    }
}
