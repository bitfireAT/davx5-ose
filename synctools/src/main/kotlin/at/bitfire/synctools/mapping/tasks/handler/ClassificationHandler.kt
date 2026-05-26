/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.tasks.handler

import android.content.ContentValues
import at.bitfire.ical4android.Task
import net.fortuna.ical4j.model.property.Clazz
import org.dmfs.tasks.contract.TaskContract.Tasks

class ClassificationHandler : DmfsTaskFieldHandler {

    override fun process(from: ContentValues, to: Task) {
        to.classification = when (from.getAsInteger(Tasks.CLASSIFICATION)) {
            Tasks.CLASSIFICATION_PUBLIC ->       Clazz(Clazz.VALUE_PUBLIC)
            Tasks.CLASSIFICATION_PRIVATE ->      Clazz(Clazz.VALUE_PRIVATE)
            Tasks.CLASSIFICATION_CONFIDENTIAL -> Clazz(Clazz.VALUE_CONFIDENTIAL)
            else ->                              null
        }
    }

}
