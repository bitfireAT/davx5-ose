/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.davtasks.builder

import android.content.Entity
import at.bitfire.tasks.contract.Tasks
import net.fortuna.ical4j.model.component.VToDo
import net.fortuna.ical4j.model.property.Color
import kotlin.jvm.optionals.getOrNull

/**
 * RFC 7986 §5.9 COLOR — stored as the raw CSS3 colour name (unlike the DMFS backend, which
 * converts to/from an Android colour int; this contract keeps the original string, per §3.2 of
 * the design doc).
 */
class ColorBuilder : DavTaskEntityBuilder {
    override fun build(from: VToDo, to: Entity) {
        val color = from.getProperty<Color>(Color.PROPERTY_NAME).getOrNull()
        to.entityValues.put(Tasks.COLOR, color?.value)
    }
}
