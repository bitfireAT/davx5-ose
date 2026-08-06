/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.tasks.contract

import android.net.Uri
import android.provider.BaseColumns

/**
 * RFC 5545 §3.6.6 VALARM. Own table (not a [TaskProperties] mimetype) because VALARM is a
 * sub-component with its own multi-valued children ([AlarmProperties]).
 */
object TaskAlarms {

    const val PATH = "alarms"

    fun contentUri(authority: String): Uri = TasksContract.contentUri(authority, PATH)

    /** `PROV` */
    const val _ID = BaseColumns._ID

    /** `PROV` — foreign key to [Tasks._ID]. Inserting/updating/deleting an alarm dirties the parent task. */
    const val TASK_ID = "task_id"

    /** RFC 5545 §3.8.6.1 ACTION. See [Action]. */
    const val ACTION = "action"

    /** RFC 5545 §3.8.6.3 TRIGGER, relative duration form (ISO 8601 duration string). */
    const val TRIGGER_RELATIVE = "trigger_relative"

    /** RFC 5545 §3.2.14 RELATED parameter of a relative TRIGGER. See [Related]. */
    const val TRIGGER_RELATED = "trigger_related"

    /** RFC 5545 §3.8.6.3 TRIGGER, absolute DATE-TIME form, ms since epoch (UTC). */
    const val TRIGGER_ABSOLUTE = "trigger_absolute"

    /** RFC 5545 §3.8.2.5 DURATION of the alarm's repeat interval (used together with [REPEAT]). */
    const val DURATION = "duration"

    /** RFC 5545 §3.8.6.2 REPEAT — number of additional repetitions. */
    const val REPEAT = "repeat"

    /** RFC 5545 §3.8.1.5 DESCRIPTION of the alarm (required for DISPLAY/EMAIL actions). */
    const val DESCRIPTION = "description"

    /** RFC 5545 §3.8.1.12 SUMMARY of the alarm (required for EMAIL action). */
    const val SUMMARY = "summary"

    /** RFC 9074 UID of the alarm. */
    const val UID = "uid"

    /** RFC 9074 ACKNOWLEDGED, ms since epoch (UTC). */
    const val ACKNOWLEDGED = "acknowledged"

    /** RFC 9074 RELATED-TO (snooze reference to another alarm's [UID]). */
    const val RELATED_TO = "related_to"

    /** RFC 9074 PROXIMITY. See [Proximity]. */
    const val PROXIMITY = "proximity"


    /** RFC 5545 §3.8.6.1 ACTION values. */
    object Action {
        const val AUDIO = "AUDIO"
        const val DISPLAY = "DISPLAY"
        const val EMAIL = "EMAIL"
    }

    /** RFC 5545 §3.2.14 RELATED parameter values. */
    object Related {
        const val START = "START"
        const val END = "END"
    }

    /** RFC 9074 PROXIMITY values. */
    object Proximity {
        const val ARRIVE = "ARRIVE"
        const val DEPART = "DEPART"
        const val CONNECT = "CONNECT"
        const val DISCONNECT = "DISCONNECT"
    }

}

/**
 * RFC 5545 §3.8.4.1 ATTENDEE (EMAIL action) and §3.8.1.1 ATTACH (AUDIO action) attached to a
 * [TaskAlarms] row. Same generic `data1..dataN` sub-row style as [TaskProperties] (D4).
 */
object AlarmProperties {

    const val PATH = "alarm_properties"

    fun contentUri(authority: String): Uri = TasksContract.contentUri(authority, PATH)

    /** `PROV` */
    const val _ID = BaseColumns._ID

    /** `PROV` — foreign key to [TaskAlarms._ID]. */
    const val ALARM_ID = "alarm_id"

    /** `PROV` — one of the `MIMETYPE_*` constants below. */
    const val MIMETYPE = "mimetype"

    const val DATA1 = "data1"
    const val DATA2 = "data2"
    const val DATA3 = "data3"

    private const val MIMETYPE_PREFIX = "vnd.android.cursor.item/vnd.at.bitfire.tasks.alarm."

    /** RFC 5545 §3.8.4.1 ATTENDEE of an EMAIL alarm. [DATA1] = CAL-ADDRESS. */
    const val MIMETYPE_ATTENDEE = MIMETYPE_PREFIX + "attendee"

    /** RFC 5545 §3.8.1.1 ATTACH of an AUDIO alarm. [DATA1] = URI, [DATA2] = FMTTYPE (§3.2.8). */
    const val MIMETYPE_ATTACHMENT = MIMETYPE_PREFIX + "attachment"

}
