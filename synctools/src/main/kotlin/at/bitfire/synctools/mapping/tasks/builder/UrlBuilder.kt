/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.tasks.builder

import android.content.Entity
import net.fortuna.ical4j.model.component.VToDo
import net.fortuna.ical4j.model.property.Url
import org.dmfs.tasks.contract.TaskContract.Tasks
import kotlin.jvm.optionals.getOrNull

class UrlBuilder : DmfsTaskEntityBuilder {

    override fun build(from: VToDo, to: Entity) {
        val url = from.getProperty<Url>(Url.URL).getOrNull()
        to.entityValues.put(Tasks.URL, url?.value)
    }

}
