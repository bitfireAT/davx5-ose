/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.tasks.builder

import android.content.Entity
import net.fortuna.ical4j.model.component.VToDo
import net.fortuna.ical4j.model.property.DtStart
import net.fortuna.ical4j.model.property.Due
import net.fortuna.ical4j.model.property.Duration
import org.dmfs.tasks.contract.TaskContract.Tasks
import java.util.logging.Logger
import kotlin.jvm.optionals.getOrNull

class DurationBuilder : DmfsTaskEntityBuilder {

    private val logger
        get() = Logger.getLogger(javaClass.name)

    override fun build(from: VToDo, to: Entity) {
        val duration = from.getProperty<Duration>(Duration.DURATION).getOrNull()
        val value = when {
            duration == null ->
                null

            // tasks provider rejects tasks with both DUE and DURATION (invalid per RFC 5545, but sent by some servers)
            from.getProperty<Due<*>>(Due.DUE).isPresent -> {
                logger.warning("Ignoring DURATION because DUE is present")
                null
            }

            from.getProperty<DtStart<*>>(DtStart.DTSTART).isEmpty -> {
                logger.warning("Ignoring DURATION without DTSTART")
                null
            }

            else ->
                duration.value
        }
        to.entityValues.put(Tasks.DURATION, value)
    }

}
