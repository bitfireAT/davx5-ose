/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.davtasks.builder

import android.content.Entity
import at.bitfire.tasks.contract.Tasks
import net.fortuna.ical4j.model.component.VToDo
import net.fortuna.ical4j.model.property.Clazz
import net.fortuna.ical4j.model.property.immutable.ImmutableClazz
import kotlin.jvm.optionals.getOrNull

class ClassificationBuilder : DavTaskEntityBuilder {
    override fun build(from: VToDo, to: Entity) {
        val clazz = from.getProperty<Clazz>(Clazz.CLASS).getOrNull()
        to.entityValues.put(Tasks.CLASSIFICATION, when (clazz?.value?.uppercase()) {
            ImmutableClazz.VALUE_PUBLIC       -> Tasks.Classification.PUBLIC
            ImmutableClazz.VALUE_CONFIDENTIAL -> Tasks.Classification.CONFIDENTIAL
            null                              -> null
            else                              -> Tasks.Classification.PRIVATE // unknown classifications MUST be treated as PRIVATE
        })
    }
}
