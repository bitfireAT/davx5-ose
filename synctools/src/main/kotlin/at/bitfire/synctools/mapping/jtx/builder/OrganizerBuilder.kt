/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.jtx.builder

import android.content.ContentValues
import android.content.Entity
import androidx.core.content.contentValuesOf
import at.techbee.jtx.JtxContract
import net.fortuna.ical4j.model.Parameter
import net.fortuna.ical4j.model.Property
import net.fortuna.ical4j.model.component.CalendarComponent
import net.fortuna.ical4j.model.parameter.Cn
import net.fortuna.ical4j.model.parameter.Dir
import net.fortuna.ical4j.model.parameter.Language
import net.fortuna.ical4j.model.parameter.SentBy
import net.fortuna.ical4j.model.property.Organizer
import kotlin.jvm.optionals.getOrNull

class OrganizerBuilder : JtxObjectEntityBuilder {
    override fun build(from: CalendarComponent, main: CalendarComponent, to: Entity) {
        val organizer = from.getProperty<Organizer>(Property.ORGANIZER).getOrNull()
        if (organizer != null) {
            val organizerValues = buildOrganizer(organizer)
            // Only add organizer if caladdress exists (otherwise an empty ORGANIZER is created)
            if (organizerValues.getAsString(JtxContract.JtxOrganizer.CALADDRESS)?.isNotEmpty() == true)
                to.addSubValue(JtxContract.JtxOrganizer.CONTENT_URI, organizerValues)
        }
    }

    private fun buildOrganizer(organizer: Organizer): ContentValues {
        val calAddress = organizer.calAddress?.toString()
        val cn = organizer.getParameter<Cn>(Parameter.CN).getOrNull()?.value
        val dir = organizer.getParameter<Dir>(Parameter.DIR).getOrNull()?.value
        val sentBy = organizer.getParameter<SentBy>(Parameter.SENT_BY).getOrNull()?.value
        val language = organizer.getParameter<Language>(Parameter.LANGUAGE).getOrNull()?.value

        val otherParameters = organizer.parameterList.removeAll(
            Parameter.CN, Parameter.DIR, Parameter.SENT_BY, Parameter.LANGUAGE
        )
        val other = JtxContract.getJsonStringFromXParameters(otherParameters)

        return contentValuesOf(
            JtxContract.JtxOrganizer.CALADDRESS to calAddress,
            JtxContract.JtxOrganizer.CN to cn,
            JtxContract.JtxOrganizer.DIR to dir,
            JtxContract.JtxOrganizer.SENTBY to sentBy,
            JtxContract.JtxOrganizer.LANGUAGE to language,
            JtxContract.JtxOrganizer.OTHER to other
        )
    }
}
