/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.tasks.builder

import android.content.Entity
import at.bitfire.synctools.icalendar.Css3Color
import net.fortuna.ical4j.model.component.VToDo
import net.fortuna.ical4j.model.property.Color
import org.dmfs.tasks.contract.TaskContract.Tasks
import kotlin.jvm.optionals.getOrNull

class ColorBuilder : DmfsTaskFieldBuilder, DmfsTaskFieldBuilderVToDo {

    override fun build(from: Task, to: Entity) {
        to.entityValues.put(Tasks.TASK_COLOR, from.color)
    }

    override fun build(from: VToDo, to: Entity) {
        val color = from.getProperty<Color>(Color.PROPERTY_NAME).getOrNull()
        to.entityValues.put(Tasks.TASK_COLOR, color?.value?.let(Css3Color::colorFromString))
    }

}
