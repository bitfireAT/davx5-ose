/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.davtasks.builder

import android.content.Entity
import at.bitfire.tasks.contract.Tasks
import net.fortuna.ical4j.model.Parameter
import net.fortuna.ical4j.model.component.VToDo
import net.fortuna.ical4j.model.parameter.Cn
import net.fortuna.ical4j.model.parameter.SentBy
import net.fortuna.ical4j.model.property.Organizer
import kotlin.jvm.optionals.getOrNull

/**
 * RFC 5545 §3.8.4.3 ORGANIZER, with CN (§3.2.2) and SENT-BY (§3.2.18) — unlike the DMFS backend,
 * which drops these parameters (see §1 of the design doc, "params dropped").
 */
class OrganizerBuilder : DavTaskEntityBuilder {
    override fun build(from: VToDo, to: Entity) {
        val organizer = from.getProperty<Organizer>(Organizer.ORGANIZER).getOrNull()
        if (organizer == null) {
            to.entityValues.putNull(Tasks.ORGANIZER)
            to.entityValues.putNull(Tasks.ORGANIZER_CN)
            to.entityValues.putNull(Tasks.ORGANIZER_SENT_BY)
            return
        }

        to.entityValues.put(Tasks.ORGANIZER, organizer.calAddress?.toString())
        to.entityValues.put(Tasks.ORGANIZER_CN, organizer.getParameter<Cn>(Parameter.CN).getOrNull()?.value)
        to.entityValues.put(Tasks.ORGANIZER_SENT_BY, organizer.getParameter<SentBy>(Parameter.SENT_BY).getOrNull()?.value)
    }
}
