/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.davtasks.builder

import android.content.Entity
import at.bitfire.tasks.contract.Tasks
import net.fortuna.ical4j.model.component.VToDo
import net.fortuna.ical4j.model.property.Url
import kotlin.jvm.optionals.getOrNull

class UrlBuilder : DavTaskEntityBuilder {
    override fun build(from: VToDo, to: Entity) {
        val url = from.getProperty<Url>(Url.URL).getOrNull()
        to.entityValues.put(Tasks.URL, url?.value)
    }
}
