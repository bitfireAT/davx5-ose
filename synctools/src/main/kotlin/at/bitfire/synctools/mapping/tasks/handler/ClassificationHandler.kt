/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.tasks.handler

import android.content.ContentValues
import android.content.Entity
import at.bitfire.ical4android.Task
import at.bitfire.synctools.icalendar.plusAssign
import net.fortuna.ical4j.model.component.VToDo
import net.fortuna.ical4j.model.property.Clazz
import net.fortuna.ical4j.model.property.immutable.ImmutableClazz
import org.dmfs.tasks.contract.TaskContract.Tasks

class ClassificationHandler : DmfsTaskFieldHandler, DmfsTaskFieldHandler2 {

    override fun process(from: ContentValues, to: Task) {
        to.classification = when (from.getAsInteger(Tasks.CLASSIFICATION)) {
            Tasks.CLASSIFICATION_PUBLIC ->       Clazz(Clazz.VALUE_PUBLIC)
            Tasks.CLASSIFICATION_PRIVATE ->      Clazz(Clazz.VALUE_PRIVATE)
            Tasks.CLASSIFICATION_CONFIDENTIAL -> Clazz(Clazz.VALUE_CONFIDENTIAL)
            else ->                              null
        }
    }

    override fun process(from: Entity, main: Entity, to: VToDo) {
        val classification = when (from.entityValues.getAsInteger(Tasks.CLASSIFICATION)) {
            Tasks.CLASSIFICATION_PUBLIC -> ImmutableClazz.PUBLIC
            Tasks.CLASSIFICATION_PRIVATE -> ImmutableClazz.PRIVATE
            Tasks.CLASSIFICATION_CONFIDENTIAL -> ImmutableClazz.CONFIDENTIAL
            else -> null
        }

        if (classification != null) {
            to += classification
        }
    }
}
