/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.mapping.tasks.builder

import android.content.Entity
import at.bitfire.ical4android.Task
import net.fortuna.ical4j.model.property.immutable.ImmutableClazz
import org.dmfs.tasks.contract.TaskContract.Tasks

class ClassificationBuilder : DmfsTaskFieldBuilder {

    override fun build(from: Task, to: Entity) {
        to.entityValues.put(Tasks.CLASSIFICATION, when (from.classification?.value?.uppercase()) {
            ImmutableClazz.VALUE_PUBLIC       -> Tasks.CLASSIFICATION_PUBLIC
            ImmutableClazz.VALUE_CONFIDENTIAL -> Tasks.CLASSIFICATION_CONFIDENTIAL
            null                              -> Tasks.CLASSIFICATION_DEFAULT
            else                              -> Tasks.CLASSIFICATION_PRIVATE    // all unknown classifications MUST be treated as PRIVATE
        })
    }

}
