/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.jtx.builder

import android.content.Entity
import androidx.core.content.contentValuesOf
import at.techbee.jtx.JtxContract
import net.fortuna.ical4j.model.Parameter
import net.fortuna.ical4j.model.Property
import net.fortuna.ical4j.model.component.CalendarComponent
import net.fortuna.ical4j.model.parameter.AltRep
import net.fortuna.ical4j.model.parameter.Language
import net.fortuna.ical4j.model.property.Comment
import kotlin.jvm.optionals.getOrNull

class CommentsBuilder : JtxObjectEntityBuilder {
    override fun build(from: CalendarComponent, main: CalendarComponent, to: Entity) {
        for (comment in from.getProperties<Comment>(Property.COMMENT)) {
            val text = comment.value
            val language = comment.getParameter<Language>(Parameter.LANGUAGE).getOrNull()?.value
            val altRep = comment.getParameter<AltRep>(Parameter.ALTREP).getOrNull()?.value

            val otherParameters = comment.parameterList.removeAll(
                Parameter.LANGUAGE,
                Parameter.ALTREP
            )
            val others = JtxContract.getJsonStringFromXParameters(otherParameters)

            to.addSubValue(
                JtxContract.JtxComment.CONTENT_URI,
                contentValuesOf(
                    JtxContract.JtxComment.TEXT to text,
                    JtxContract.JtxComment.ALTREP to altRep,
                    JtxContract.JtxComment.LANGUAGE to language,
                    JtxContract.JtxComment.OTHER to others,
                    JtxContract.JtxComment.ID to 0L
                )
            )
        }
    }
}
