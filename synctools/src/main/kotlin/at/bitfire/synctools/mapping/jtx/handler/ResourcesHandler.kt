/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.jtx.handler

import android.content.Entity
import at.bitfire.synctools.icalendar.plusAssign
import at.techbee.jtx.JtxContract
import net.fortuna.ical4j.model.component.CalendarComponent
import net.fortuna.ical4j.model.component.VJournal
import net.fortuna.ical4j.model.parameter.Language
import net.fortuna.ical4j.model.property.Resources

class ResourcesHandler : JtxObjectEntityHandler {
    override fun process(
        from: Entity,
        main: Entity,
        to: CalendarComponent
    ) {
        if (to is VJournal) return

        val resourceTexts = from.subValues.filter { it.uri == JtxContract.JtxResource.CONTENT_URI }

        for (resourceValues in resourceTexts) {
            val contentValues = resourceValues.values
            val text = contentValues.getAsString(JtxContract.JtxResource.TEXT)

            val resources = Resources(text)

            contentValues.getAsString(JtxContract.JtxResource.LANGUAGE)?.let { lang ->
                resources += Language(lang)
            }
            contentValues.getAsString(JtxContract.JtxResource.OTHER)?.let { other ->
                val otherParameters = JtxContract.getXParametersFromJson(other)
                for (parameter in otherParameters) {
                    resources += parameter
                }
            }

            to += resources
        }
    }
}
