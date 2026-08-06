/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.davtasks.builder

import android.content.Entity
import androidx.core.content.contentValuesOf
import at.bitfire.synctools.storage.davtasks.DavTaskList
import at.bitfire.tasks.contract.TaskProperties
import net.fortuna.ical4j.model.Parameter
import net.fortuna.ical4j.model.Property
import net.fortuna.ical4j.model.component.VToDo
import net.fortuna.ical4j.model.parameter.FmtType
import net.fortuna.ical4j.model.property.Attach
import java.util.logging.Logger
import kotlin.jvm.optionals.getOrNull

/**
 * RFC 5545 §3.8.1.1 ATTACH — URI form only for v1. Inline BINARY attachments (blob storage via
 * `openFile()`) are Phase 4 (design doc §6) and not yet implemented; such attachments are
 * currently dropped (logged) rather than silently corrupted.
 */
class AttachmentsBuilder(
    private val taskList: DavTaskList
) : DavTaskEntityBuilder {

    private val logger
        get() = Logger.getLogger(javaClass.name)

    override fun build(from: VToDo, to: Entity) {
        for (attach in from.getProperties<Attach>(Property.ATTACH)) {
            val uri = attach.uri?.toString()
            if (uri == null) {
                logger.warning("Ignoring inline BINARY attachment (blob storage not yet implemented, Phase 4)")
                continue
            }
            to.addSubValue(
                taskList.tasksPropertiesUri(asSyncAdapter = false),
                contentValuesOf(
                    TaskProperties.MIMETYPE to TaskProperties.MIMETYPE_ATTACHMENT,
                    TaskProperties.DATA1 to uri,
                    TaskProperties.DATA2 to attach.getParameter<FmtType>(Parameter.FMTTYPE).getOrNull()?.value
                )
            )
        }
    }
}
