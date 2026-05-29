/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.tasks.handler

import android.content.ContentValues
import at.bitfire.ical4android.Task
import net.fortuna.ical4j.model.parameter.RelType
import net.fortuna.ical4j.model.property.RelatedTo
import org.dmfs.tasks.contract.TaskContract.Property.Relation
import java.util.logging.Logger

class RelationsHandler : DmfsTaskPropertyHandler {

    private val logger
        get() = Logger.getLogger(javaClass.name)

    override fun process(row: ContentValues, to: Task) {
        val uid = row.getAsString(Relation.RELATED_UID)
        if (uid == null) {
            logger.warning("Task relation doesn't refer to same task list; can't be synchronized")
            return
        }

        to.relatedTo.add(
            RelatedTo(uid)
                // add relation type as RELTYPE parameter
                .add(
                    when (row.getAsInteger(Relation.RELATED_TYPE)) {
                        Relation.RELTYPE_CHILD ->
                            RelType.CHILD
                        Relation.RELTYPE_SIBLING ->
                            RelType.SIBLING
                        else /* Relation.RELTYPE_PARENT, default value */ ->
                            RelType.PARENT
                    }
                )
        )
    }
}
