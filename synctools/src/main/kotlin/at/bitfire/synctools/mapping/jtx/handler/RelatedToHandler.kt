/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.jtx.handler

import android.content.ContentValues
import android.content.Entity
import at.bitfire.synctools.icalendar.plusAssign
import at.techbee.jtx.JtxContract
import net.fortuna.ical4j.model.ParameterList
import net.fortuna.ical4j.model.component.CalendarComponent
import net.fortuna.ical4j.model.parameter.RelType
import net.fortuna.ical4j.model.property.RelatedTo
import java.util.logging.Logger

class RelatedToHandler : JtxObjectEntityHandler {

    private val logger
        get() = Logger.getLogger(javaClass.name)

    override fun process(from: Entity, main: Entity, to: CalendarComponent) {
        for (row in from.subValues.filter { it.uri == JtxContract.JtxRelatedto.CONTENT_URI }) {
            processRelatedTo(row.values, to)
        }
    }

    private fun processRelatedTo(values: ContentValues, to: CalendarComponent) {
        val text = values.getAsString(JtxContract.JtxRelatedto.TEXT)
        val relType = when (val value = values.getAsString(JtxContract.JtxRelatedto.RELTYPE)) {
            JtxContract.JtxRelatedto.Reltype.CHILD.name -> RelType.CHILD
            JtxContract.JtxRelatedto.Reltype.PARENT.name -> RelType.PARENT
            JtxContract.JtxRelatedto.Reltype.SIBLING.name -> RelType.SIBLING
            else -> {
                logger.warning("RELATED-TO row has unsupported RELTYPE: $value; skipping")
                return
            }
        }

        val relatedTo = RelatedTo(ParameterList(listOf(relType)), text)
        values.getAsString(JtxContract.JtxRelatedto.OTHER)?.let { other ->
            val otherParameters = JtxContract.getXParametersFromJson(other)
            for (parameter in otherParameters) {
                relatedTo += parameter
            }
        }

        to += relatedTo
    }
}
