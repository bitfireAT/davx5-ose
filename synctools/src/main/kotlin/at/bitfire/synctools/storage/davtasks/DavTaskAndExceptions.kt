/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.storage.davtasks

import at.bitfire.synctools.storage.MainItemAndExceptions

/**
 * Represents a potentially recurring main task and optional exceptions.
 *
 * v1 note: exceptions are always empty for now (see [DavRecurringTaskList]); RECURRENCE-ID
 * override support is Phase 3 work (design doc, matching the pre-existing limitation of
 * [at.bitfire.synctools.mapping.tasks.DmfsTaskBuilder], #2357).
 */
typealias DavTaskAndExceptions = MainItemAndExceptions
