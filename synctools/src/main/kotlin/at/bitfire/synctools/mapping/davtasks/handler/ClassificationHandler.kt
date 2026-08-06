/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.davtasks.handler

import android.content.Entity
import at.bitfire.synctools.icalendar.plusAssign
import at.bitfire.tasks.contract.Tasks
import net.fortuna.ical4j.model.component.VToDo
import net.fortuna.ical4j.model.property.immutable.ImmutableClazz

class ClassificationHandler : DavTaskEntityHandler {
    override fun process(from: Entity, main: Entity, to: VToDo) {
        val classification = when (from.entityValues.getAsString(Tasks.CLASSIFICATION)) {
            Tasks.Classification.PUBLIC -> ImmutableClazz.PUBLIC
            Tasks.Classification.PRIVATE -> ImmutableClazz.PRIVATE
            Tasks.Classification.CONFIDENTIAL -> ImmutableClazz.CONFIDENTIAL
            else -> null
        }
        if (classification != null)
            to += classification
    }
}
