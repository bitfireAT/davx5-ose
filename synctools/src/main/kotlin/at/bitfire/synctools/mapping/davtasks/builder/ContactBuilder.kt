/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.davtasks.builder

import android.content.Entity
import androidx.core.content.contentValuesOf
import at.bitfire.synctools.storage.davtasks.DavTaskList
import at.bitfire.tasks.contract.TaskProperties
import net.fortuna.ical4j.model.Parameter
import net.fortuna.ical4j.model.component.VToDo
import net.fortuna.ical4j.model.parameter.AltRep
import net.fortuna.ical4j.model.parameter.Language
import net.fortuna.ical4j.model.property.Contact
import kotlin.jvm.optionals.getOrNull

/** RFC 5545 §3.8.4.2 CONTACT (one row per value). */
class ContactBuilder(
    private val taskList: DavTaskList
) : DavTaskEntityBuilder {
    override fun build(from: VToDo, to: Entity) {
        for (contact in from.getProperties<Contact>(Contact.CONTACT)) {
            to.addSubValue(
                taskList.tasksPropertiesUri(asSyncAdapter = false),
                contentValuesOf(
                    TaskProperties.MIMETYPE to TaskProperties.MIMETYPE_CONTACT,
                    TaskProperties.DATA1 to contact.value,
                    TaskProperties.DATA2 to contact.getParameter<AltRep>(Parameter.ALTREP).getOrNull()?.value,
                    TaskProperties.DATA3 to contact.getParameter<Language>(Parameter.LANGUAGE).getOrNull()?.value
                )
            )
        }
    }
}
