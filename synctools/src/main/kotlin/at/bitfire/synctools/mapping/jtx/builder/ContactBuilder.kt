/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.jtx.builder

import android.content.Entity
import at.techbee.jtx.JtxContract
import net.fortuna.ical4j.model.component.CalendarComponent
import net.fortuna.ical4j.model.property.Contact
import kotlin.jvm.optionals.getOrNull

class ContactBuilder : JtxObjectEntityBuilder {
    override fun build(from: CalendarComponent, main: CalendarComponent, to: Entity) {
        val contact = from.getProperty<Contact>(Contact.CONTACT)?.getOrNull()?.value
        to.entityValues.put(JtxContract.JtxICalObject.CONTACT, contact)
    }
}
