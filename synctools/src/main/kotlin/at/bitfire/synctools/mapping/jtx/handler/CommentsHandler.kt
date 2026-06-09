/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.jtx.handler

import android.content.Entity
import at.bitfire.synctools.icalendar.plusAssign
import at.techbee.jtx.JtxContract
import net.fortuna.ical4j.model.component.CalendarComponent
import net.fortuna.ical4j.model.parameter.AltRep
import net.fortuna.ical4j.model.parameter.Language
import net.fortuna.ical4j.model.property.Comment

class CommentsHandler : JtxObjectEntityHandler {
    override fun process(
        from: Entity,
        main: Entity,
        to: CalendarComponent
    ) {
        val commentTexts = from.subValues
            .filter { it.uri == JtxContract.JtxComment.CONTENT_URI }

        for (commentValues in commentTexts) {
            val text = commentValues.values.getAsString(JtxContract.JtxComment.TEXT) ?: continue

            val comment = Comment(text)

            commentValues.values.getAsString(JtxContract.JtxComment.ALTREP)?.let { altRep ->
                comment += AltRep(altRep)
            }
            commentValues.values.getAsString(JtxContract.JtxComment.LANGUAGE)?.let { lang ->
                comment += Language(lang)
            }
            commentValues.values.getAsString(JtxContract.JtxComment.OTHER)?.let { other ->
                val otherParameters = JtxContract.getXParametersFromJson(other)
                for (parameter in otherParameters) {
                    comment += parameter
                }
            }

            to += comment
        }
    }
}
