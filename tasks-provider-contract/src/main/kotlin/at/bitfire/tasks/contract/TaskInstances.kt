/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.tasks.contract

import android.net.Uri
import android.provider.BaseColumns

/**
 * Provider-maintained, read-only expansion of recurring tasks (RFC 5545 §3.8.5 RRULE/RDATE/EXDATE)
 * into concrete occurrences. Frontends read this table instead of expanding RRULE themselves (D3).
 *
 * v1 note: every non-recurring task has exactly one row here (itself). Full RRULE expansion
 * (multiple rows per recurring task, DST/all-day correctness, RECURRENCE-ID override folding) is
 * tracked as Phase 3 in the design doc and not yet implemented — the table exists so frontends can
 * already be written against it.
 */
object TaskInstances {

    const val PATH = "instances"

    fun contentUri(authority: String): Uri = TasksContract.contentUri(authority, PATH)

    /** `PROV` */
    const val _ID = BaseColumns._ID

    /** `PROV` — foreign key to [Tasks._ID]. */
    const val TASK_ID = "task_id"

    /** derived from RFC 5545 §3.8.5 expansion; ms since epoch. */
    const val INSTANCE_START = "instance_start"

    /** derived from RFC 5545 §3.8.5 expansion; ms since epoch. */
    const val INSTANCE_DUE = "instance_due"

    /** `PROV` — local-time-normalised so `ORDER BY` works across timezones. */
    const val INSTANCE_START_SORTING = "instance_start_sorting"

    /** `PROV` — local-time-normalised so `ORDER BY` works across timezones. */
    const val INSTANCE_DUE_SORTING = "instance_due_sorting"

    /** `PROV` — signed distance from the current instance (0), for "next up" queries. */
    const val DISTANCE_FROM_CURRENT = "distance_from_current"

}
