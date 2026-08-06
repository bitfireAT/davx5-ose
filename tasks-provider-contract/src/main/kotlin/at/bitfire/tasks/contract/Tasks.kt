/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.tasks.contract

import android.net.Uri
import android.provider.BaseColumns

/**
 * Table of tasks — one row per VTODO component (main occurrence, or a RECURRENCE-ID override).
 *
 * The provider enforces the RFC 5545 §3.6.2 grammar constraints: [DUE] xor [DURATION];
 * [DURATION] implies [DTSTART]; the main row (no [ORIGINAL_INSTANCE_ID]) has no RECURRENCE-ID.
 */
object Tasks {

    const val PATH = "tasks"

    fun contentUri(authority: String): Uri = TasksContract.contentUri(authority, PATH)

    /** `PROV` */
    const val _ID = BaseColumns._ID

    /** `PROV` — foreign key to [TaskLists._ID]. */
    const val LIST_ID = "list_id"

    /** RFC 5545 §3.8.4.7 UID. */
    const val _UID = "_uid"

    /** `SYNC` — file name / resource name on the CalDAV server. */
    const val _SYNC_ID = "_sync_id"

    /** `SYNC` — `1` if this row has local changes not yet uploaded. */
    const val _DIRTY = "_dirty"

    /** `SYNC` — `1` if this row is a tombstone awaiting upload of the deletion. */
    const val _DELETED = "_deleted"

    /** `SYNC` — serialized `SyncState` (ETag etc.) of the last sync. */
    const val SYNC_VERSION = "sync_version"

    /** `SYNC` — sync-adapter private. */
    const val SYNC1 = "sync1"
    /** `SYNC` — sync-adapter private. */
    const val SYNC2 = "sync2"
    /** `SYNC` — sync-adapter private. */
    const val SYNC3 = "sync3"
    /** `SYNC` — sync-adapter private. */
    const val SYNC4 = "sync4"

    /**
     * RFC 5545 §3.8.4.4 RECURRENCE-ID — [_ID] of the main task this row is an exception of.
     * `null` for the main row.
     */
    const val ORIGINAL_INSTANCE_ID = "original_instance_id"

    /** RFC 5545 §3.8.4.4 RECURRENCE-ID value, ms since epoch (UTC). */
    const val ORIGINAL_INSTANCE_TIME = "original_instance_time"

    /** RFC 5545 §3.8.4.4 — whether [ORIGINAL_INSTANCE_TIME] is a DATE (not DATE-TIME) value. */
    const val ORIGINAL_INSTANCE_ALLDAY = "original_instance_allday"

    /** RFC 5545 §3.8.1.12 SUMMARY. */
    const val SUMMARY = "summary"

    /** RFC 5545 §3.8.1.5 DESCRIPTION. */
    const val DESCRIPTION = "description"

    /** RFC 5545 §3.8.1.7 LOCATION. */
    const val LOCATION = "location"

    /** RFC 5545 §3.8.1.6 GEO — latitude part. */
    const val GEO_LAT = "geo_lat"

    /** RFC 5545 §3.8.1.6 GEO — longitude part. */
    const val GEO_LON = "geo_lon"

    /** RFC 5545 §3.8.4.6 URL. */
    const val URL = "url"

    /** RFC 7986 §5.9 COLOR — CSS3 colour name. */
    const val COLOR = "color"

    /** RFC 5545 §3.8.4.3 ORGANIZER (CAL-ADDRESS). */
    const val ORGANIZER = "organizer"

    /** RFC 5545 §3.2.2 CN parameter of ORGANIZER. */
    const val ORGANIZER_CN = "organizer_cn"

    /** RFC 5545 §3.2.18 SENT-BY parameter of ORGANIZER. */
    const val ORGANIZER_SENT_BY = "organizer_sent_by"

    /** RFC 5545 §3.8.1.9 PRIORITY, 0–9. */
    const val PRIORITY = "priority"

    /** RFC 5545 §3.8.1.3 CLASS. See [Classification]. */
    const val CLASSIFICATION = "classification"

    /** RFC 5545 §3.8.1.11 STATUS (VTODO value set only). See [Status]. */
    const val STATUS = "status"

    /** RFC 5545 §3.8.1.8 PERCENT-COMPLETE (VTODO-exclusive), 0–100. */
    const val PERCENT_COMPLETE = "percent_complete"

    /** RFC 5545 §3.8.2.1 COMPLETED (VTODO-exclusive), ms since epoch (UTC). */
    const val COMPLETED = "completed"

    /** Whether [COMPLETED] is a DATE (not DATE-TIME) value. */
    const val COMPLETED_ALLDAY = "completed_allday"

    /** RFC 5545 §3.8.7.4 SEQUENCE. */
    const val SEQUENCE = "sequence"

    /** RFC 5545 §3.8.7.1 CREATED, ms since epoch (UTC). */
    const val CREATED = "created"

    /** RFC 5545 §3.8.7.3 LAST-MODIFIED, ms since epoch (UTC). */
    const val LAST_MODIFIED = "last_modified"

    /** RFC 5545 §3.8.7.2 DTSTAMP, ms since epoch (UTC). Regenerated on every write. */
    const val DTSTAMP = "dtstamp"

    /** RFC 5545 §3.8.2.4 DTSTART, ms since epoch. */
    const val DTSTART = "dtstart"

    /** RFC 5545 §3.2.19 TZID parameter of DTSTART (Olson ID), or `null`/`"UTC"`. */
    const val DTSTART_TZ = "dtstart_tz"

    /** RFC 5545 §3.8.2.3 DUE (VTODO-exclusive), ms since epoch. Mutually exclusive with [DURATION]. */
    const val DUE = "due"

    /** TZID parameter of DUE. */
    const val DUE_TZ = "due_tz"

    /** RFC 5545 §3.8.2.5 DURATION. Requires [DTSTART] to be set (§3.6.2). ISO 8601 duration string. */
    const val DURATION = "duration"

    /** RFC 5545 §3.3.4 (DATE) vs §3.3.5 (DATE-TIME) value type of [DTSTART]/[DUE]. */
    const val IS_ALLDAY = "is_allday"

    /** RFC 5545 §3.8.5.3 RRULE (RECUR, §3.3.10) — the raw RRULE value, e.g. `FREQ=DAILY;COUNT=5`. */
    const val RRULE = "rrule"

    /**
     * RFC 5545 §3.8.5.2 RDATE — comma-separated occurrence dates to add to the recurrence set
     * defined by [RRULE] (or, with no [RRULE], the complete set beyond [DTSTART] itself).
     *
     * Each value is `yyyyMMdd` if [IS_ALLDAY], otherwise `yyyyMMdd'T'HHmmss` (in [DTSTART_TZ]) or
     * `yyyyMMdd'T'HHmmss'Z'` (UTC) — the same VALUE type and time zone as [DTSTART], per §3.8.5.2.
     * Example: `20260101T090000Z,20260201T090000Z`.
     */
    const val RDATE = "rdate"

    /**
     * RFC 5545 §3.8.5.1 EXDATE — comma-separated occurrence dates to remove from the recurrence
     * set. Same value format as [RDATE].
     *
     * There is deliberately no `EXRULE` column: it is RFC 2445 legacy, removed by RFC 5545.
     * It is preserved as an opaque [TaskProperties.MIMETYPE_UNKNOWN_PROPERTY] row instead.
     */
    const val EXDATE = "exdate"


    /** RFC 5545 §3.8.1.11 STATUS values (VTODO value set only). */
    object Status {
        const val NEEDS_ACTION = "NEEDS-ACTION"
        const val IN_PROCESS = "IN-PROCESS"
        const val COMPLETED = "COMPLETED"
        const val CANCELLED = "CANCELLED"
    }

    /** RFC 5545 §3.8.1.3 CLASS values. */
    object Classification {
        const val PUBLIC = "PUBLIC"
        const val PRIVATE = "PRIVATE"
        const val CONFIDENTIAL = "CONFIDENTIAL"
    }

}
