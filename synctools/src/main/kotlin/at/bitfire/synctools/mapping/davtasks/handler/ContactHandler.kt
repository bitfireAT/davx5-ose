/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.davtasks.handler

import android.content.Entity
import at.bitfire.synctools.icalendar.plusAssign
import at.bitfire.synctools.mapping.davtasks.mimeType
import at.bitfire.tasks.contract.TaskProperties
import net.fortuna.ical4j.model.component.VToDo
import net.fortuna.ical4j.model.parameter.AltRep
import net.fortuna.ical4j.model.parameter.Language
import net.fortuna.ical4j.model.property.Contact

class ContactHandler : DavTaskEntityHandler {
    override fun process(from: Entity, main: Entity, to: VToDo) {
        for (row in from.subValues.filter { it.mimeType == TaskProperties.MIMETYPE_CONTACT }) {
            val text = row.values.getAsString(TaskProperties.DATA1) ?: continue
            val contact = Contact(text)
            row.values.getAsString(TaskProperties.DATA2)?.let { contact += AltRep(it) }
            row.values.getAsString(TaskProperties.DATA3)?.let { contact += Language(it) }
            to += contact
        }
    }
}
