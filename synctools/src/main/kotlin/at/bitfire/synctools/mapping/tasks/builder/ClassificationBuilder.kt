/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.tasks.builder

import android.content.Entity
import net.fortuna.ical4j.model.component.VToDo
import net.fortuna.ical4j.model.property.Clazz
import net.fortuna.ical4j.model.property.immutable.ImmutableClazz
import org.dmfs.tasks.contract.TaskContract.Tasks
import kotlin.jvm.optionals.getOrNull

class ClassificationBuilder : DmfsTaskEntityBuilder {

    override fun build(from: VToDo, to: Entity) {
        val clazz = from.getProperty<Clazz>(Clazz.CLASS).getOrNull()
        to.entityValues.put(Tasks.CLASSIFICATION, when (clazz?.value?.uppercase()) {
            ImmutableClazz.VALUE_PUBLIC       -> Tasks.CLASSIFICATION_PUBLIC
            ImmutableClazz.VALUE_CONFIDENTIAL -> Tasks.CLASSIFICATION_CONFIDENTIAL
            null                              -> Tasks.CLASSIFICATION_DEFAULT
            else                              -> Tasks.CLASSIFICATION_PRIVATE    // all unknown classifications MUST be treated as PRIVATE
        })
    }

}
