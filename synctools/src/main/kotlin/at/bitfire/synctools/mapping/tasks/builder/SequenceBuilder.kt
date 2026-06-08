/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.tasks.builder

import android.content.Entity
import net.fortuna.ical4j.model.component.VToDo
import net.fortuna.ical4j.model.property.Sequence
import org.dmfs.tasks.contract.TaskContract.Tasks
import kotlin.jvm.optionals.getOrNull

class SequenceBuilder : DmfsTaskEntityBuilder {

    override fun build(from: VToDo, to: Entity) {
        /* When we build the SYNC_VERSION column from a real task, we set the sequence to 0 (not null), so that we
        can distinguish it from tasks which have been created locally and have never been uploaded yet. */
        val sequence = from.getProperty<Sequence>(Sequence.SEQUENCE).getOrNull()
        to.entityValues.put(Tasks.SYNC_VERSION, sequence?.sequenceNo ?: 0)
    }

}
