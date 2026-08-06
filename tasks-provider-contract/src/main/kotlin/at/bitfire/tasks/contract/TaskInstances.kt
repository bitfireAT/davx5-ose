/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.tasks.contract

import android.net.Uri
import android.provider.BaseColumns

/**
 * Provider-maintained, read-only expansion of recurring tasks (RFC 5545 §3.8.5 RRULE/RDATE/EXDATE)
 * into concrete occurrences, with RECURRENCE-ID overrides (§3.8.4.4) folded in. Frontends read this
 * table instead of expanding RRULE themselves (D3).
 *
 * A non-recurring task has exactly one row here (itself). A RECURRENCE-ID exception row's own
 * instance replaces the occurrence it overrides — [TASK_ID] for that occurrence is the exception's
 * id, not the main task's.
 *
 * Bounded, deterministic expansion: stops at whichever of the RRULE's own COUNT/UNTIL, 500
 * occurrences, or 10 years past DTSTART comes first (see `RecurrenceExpander.MAX_INSTANCES` /
 * `MAX_HORIZON` in the provider implementation). An indefinitely-recurring task (no COUNT/UNTIL)
 * only gets instances up to that horizon — the same practical limit every mainstream calendar/task
 * app imposes. Instances are fully regenerated on every write to the task (or one of its
 * RECURRENCE-ID exceptions), so this horizon is relative to *DTSTART*, not to "now"; a task that
 * hasn't been touched in years and recurs indefinitely won't grow new instances just from the
 * passage of time.
 *
 * [DISTANCE_FROM_CURRENT] is computed against wall-clock time at that last write and therefore
 * goes stale the same way.
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
