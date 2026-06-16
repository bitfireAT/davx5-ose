/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.jtx.builder

import android.content.Entity
import at.techbee.jtx.JtxContract
import net.fortuna.ical4j.model.Property
import net.fortuna.ical4j.model.component.CalendarComponent
import net.fortuna.ical4j.model.property.Sequence
import kotlin.jvm.optionals.getOrNull

class SequenceBuilder : JtxObjectEntityBuilder {
    override fun build(from: CalendarComponent, main: CalendarComponent, to: Entity) {
        /* When we build the SEQUENCE column from a real object, we set the sequence to 0 (not null), so that we
        can distinguish it from objects which have been created locally and have never been uploaded yet. */
        val sequence = from.getProperty<Sequence>(Property.SEQUENCE).getOrNull()?.sequenceNo?.toLong() ?: 0L
        to.entityValues.put(JtxContract.JtxICalObject.SEQUENCE, sequence)
    }
}
