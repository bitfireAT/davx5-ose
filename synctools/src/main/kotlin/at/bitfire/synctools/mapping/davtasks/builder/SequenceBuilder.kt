/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.davtasks.builder

import android.content.Entity
import at.bitfire.tasks.contract.Tasks
import net.fortuna.ical4j.model.component.VToDo
import net.fortuna.ical4j.model.property.Sequence
import kotlin.jvm.optionals.getOrNull

class SequenceBuilder : DavTaskEntityBuilder {
    override fun build(from: VToDo, to: Entity) {
        // set to 0 (not null) so we can distinguish real tasks from locally-created, never-uploaded ones
        val sequence = from.getProperty<Sequence>(Sequence.SEQUENCE).getOrNull()
        to.entityValues.put(Tasks.SEQUENCE, sequence?.sequenceNo ?: 0)
    }
}
