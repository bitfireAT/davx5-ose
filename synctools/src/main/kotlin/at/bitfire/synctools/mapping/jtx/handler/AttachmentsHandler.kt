/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.jtx.handler

import android.content.Entity
import at.bitfire.synctools.icalendar.plusAssign
import at.bitfire.synctools.mapping.jtx.builder.X_PARAM_ATTACH_LABEL
import at.bitfire.synctools.mapping.jtx.builder.X_PARAM_FILENAME
import at.techbee.jtx.JtxContract
import net.fortuna.ical4j.model.component.CalendarComponent
import net.fortuna.ical4j.model.parameter.FmtType
import net.fortuna.ical4j.model.parameter.XParameter
import net.fortuna.ical4j.model.property.Attach
import java.net.URI
import java.nio.ByteBuffer
import java.util.logging.Level
import java.util.logging.Logger

class AttachmentsHandler(
    private val attachmentFetcher: AttachmentFetcher
) : JtxObjectEntityHandler {

    private val logger
        get() = Logger.getLogger(javaClass.name)

    override fun process(from: Entity, main: Entity, to: CalendarComponent) {
        val attachments = from.subValues.filter { it.uri == JtxContract.JtxAttachment.CONTENT_URI }

        for (attachmentValues in attachments) {
            buildAttachProperty(attachmentValues, to)
        }
    }

    private fun buildAttachProperty(attachmentValues: Entity.NamedContentValues, to: CalendarComponent) {
        val uriString = attachmentValues.values.getAsString(JtxContract.JtxAttachment.URI) ?: return

        val attach = if (uriString.startsWith("content://")) {
            val binaryData = attachmentFetcher.getAttachmentData(uriString)?.let { ByteBuffer.wrap(it) }
            if (binaryData == null) {
                logger.warning("Failed to read attachment data from URI: $uriString")
                return
            }

            Attach(binaryData)
        } else {
            val uri = try {
                URI.create(uriString)
            } catch (e: Exception) {
                logger.log(Level.WARNING, "Ignoring invalid attachment URI: $uriString", e)
                return
            }

            Attach(uri)
        }

        attachmentValues.values.getAsString(JtxContract.JtxAttachment.FMTTYPE)?.let { fmtType ->
            attach += FmtType(fmtType)
        }

        attachmentValues.values.getAsString(JtxContract.JtxAttachment.FILENAME)?.let { filename ->
            attach += XParameter(X_PARAM_FILENAME, filename)
            attach += XParameter(X_PARAM_ATTACH_LABEL, filename)
        }

        attachmentValues.values.getAsString(JtxContract.JtxAttachment.OTHER)?.let { other ->
            val otherParameters = JtxContract.getXParametersFromJson(other)
            for (parameter in otherParameters) {
                attach += parameter
            }
        }

        to += attach
    }
}
