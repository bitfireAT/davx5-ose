/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.tasks.contract

import android.net.Uri
import android.provider.BaseColumns

/**
 * Mimetype-discriminated sub-rows of a task (`ContactsContract`-style generic `data1..dataN`
 * columns with per-mimetype documented meaning, D4) — new mimetypes ship without a schema
 * migration.
 */
object TaskProperties {

    const val PATH = "properties"

    fun contentUri(authority: String): Uri = TasksContract.contentUri(authority, PATH)

    /** `PROV` */
    const val _ID = BaseColumns._ID

    /** `PROV` — foreign key to [Tasks._ID]. Inserting/updating/deleting a property row dirties the parent task. */
    const val TASK_ID = "task_id"

    /** `PROV` — one of the `MIMETYPE_*` constants below. */
    const val MIMETYPE = "mimetype"

    const val DATA1 = "data1"
    const val DATA2 = "data2"
    const val DATA3 = "data3"
    const val DATA4 = "data4"
    const val DATA5 = "data5"
    const val DATA6 = "data6"
    const val DATA7 = "data7"
    const val DATA8 = "data8"
    const val DATA9 = "data9"
    const val DATA10 = "data10"
    const val DATA11 = "data11"
    const val DATA12 = "data12"
    const val DATA13 = "data13"
    const val DATA14 = "data14"
    const val DATA15 = "data15"

    /** `SYNC` — sync-adapter private. */
    const val DATA_SYNC1 = "data_sync1"
    /** `SYNC` — sync-adapter private. */
    const val DATA_SYNC2 = "data_sync2"
    /** `SYNC` — sync-adapter private. */
    const val DATA_SYNC3 = "data_sync3"
    /** `SYNC` — sync-adapter private. */
    const val DATA_SYNC4 = "data_sync4"


    private const val MIMETYPE_PREFIX = "vnd.android.cursor.item/vnd.at.bitfire.tasks."

    /**
     * RFC 5545 §3.8.1.2 CATEGORIES (one row per category value).
     * [DATA1] = value, [DATA2] = LANGUAGE (§3.2.10).
     */
    const val MIMETYPE_CATEGORY = MIMETYPE_PREFIX + "category"

    /**
     * RFC 5545 §3.8.1.4 COMMENT.
     * [DATA1] = value, [DATA2] = ALTREP (§3.2.1), [DATA3] = LANGUAGE (§3.2.10).
     */
    const val MIMETYPE_COMMENT = MIMETYPE_PREFIX + "comment"

    /**
     * RFC 5545 §3.8.4.5 RELATED-TO, extended by RFC 9253 task-dependency RELTYPE values and GAP.
     * [DATA1] = target UID, [DATA2] = RELTYPE (§3.2.15, see [RelType]), [DATA3] = GAP (RFC 9253).
     */
    const val MIMETYPE_RELATION = MIMETYPE_PREFIX + "relation"

    /**
     * RFC 5545 §3.8.4.1 ATTENDEE.
     * [DATA1] = CAL-ADDRESS, [DATA2] = CN (§3.2.2), [DATA3] = CUTYPE (§3.2.3),
     * [DATA4] = DELEGATED-FROM (§3.2.4), [DATA5] = DELEGATED-TO (§3.2.5), [DATA6] = DIR (§3.2.6),
     * [DATA7] = LANGUAGE (§3.2.10), [DATA8] = MEMBER (§3.2.11), [DATA9] = PARTSTAT (§3.2.12),
     * [DATA10] = ROLE (§3.2.16), [DATA11] = RSVP (§3.2.17), [DATA12] = SENT-BY (§3.2.18),
     * [DATA13] = SCHEDULE-STATUS (RFC 6638).
     */
    const val MIMETYPE_ATTENDEE = MIMETYPE_PREFIX + "attendee"

    /**
     * RFC 5545 §3.8.1.1 ATTACH.
     * [DATA1] = URI (for inline BINARY, an `openFile()`-openable `content:` URI of this row),
     * [DATA2] = FMTTYPE (§3.2.8), [DATA3] = ENCODING (§3.2.7, e.g. `BASE64` for inline binary).
     */
    const val MIMETYPE_ATTACHMENT = MIMETYPE_PREFIX + "attachment"

    /**
     * RFC 5545 §3.8.4.2 CONTACT.
     * [DATA1] = value, [DATA2] = ALTREP (§3.2.1), [DATA3] = LANGUAGE (§3.2.10).
     */
    const val MIMETYPE_CONTACT = MIMETYPE_PREFIX + "contact"

    /**
     * RFC 5545 §3.8.1.10 RESOURCES (one row per resource value).
     * [DATA1] = value, [DATA2] = LANGUAGE (§3.2.10).
     */
    const val MIMETYPE_RESOURCE = MIMETYPE_PREFIX + "resource"

    /**
     * RFC 5545 §3.8.8.3 REQUEST-STATUS.
     * [DATA1] = statcode, [DATA2] = statdesc, [DATA3] = extdata.
     */
    const val MIMETYPE_REQUEST_STATUS = MIMETYPE_PREFIX + "request-status"

    /**
     * RFC 5545 §3.8.8.1/.2 non-standard (X-) / IANA properties DAVx⁵ doesn't otherwise map.
     * [DATA1] = `UnknownProperty` JSON (property name, value and parameters).
     *
     * There is deliberately no dedicated column for `EXRULE`: it is RFC 2445 legacy, removed by
     * RFC 5545, and is preserved here rather than dropped.
     */
    const val MIMETYPE_UNKNOWN_PROPERTY = MIMETYPE_PREFIX + "unknown-property"

    /**
     * RFC 7986 §5.10 IMAGE. Reserved for v2 — not yet populated by the provider.
     */
    const val MIMETYPE_IMAGE = MIMETYPE_PREFIX + "image"

    /**
     * RFC 7986 §5.11 CONFERENCE. Reserved for v2 — not yet populated by the provider.
     */
    const val MIMETYPE_CONFERENCE = MIMETYPE_PREFIX + "conference"

    /**
     * RFC 9253 LINK / CONCEPT / REFID. Reserved for v2 — not yet populated by the provider.
     */
    const val MIMETYPE_LINK = MIMETYPE_PREFIX + "link"
    const val MIMETYPE_CONCEPT = MIMETYPE_PREFIX + "concept"
    const val MIMETYPE_REFID = MIMETYPE_PREFIX + "refid"


    /** RFC 5545 §3.2.15 RELTYPE values, extended by RFC 9253 task-dependency values. */
    object RelType {
        // RFC 5545 §3.2.15
        const val PARENT = "PARENT"
        const val CHILD = "CHILD"
        const val SIBLING = "SIBLING"

        // RFC 9253 — task dependencies
        const val DEPENDS_ON = "DEPENDS-ON"
        const val FINISHTOSTART = "FINISHTOSTART"
        const val FINISHTOFINISH = "FINISHTOFINISH"
        const val STARTTOSTART = "STARTTOSTART"
        const val STARTTOFINISH = "STARTTOFINISH"
        const val FIRST = "FIRST"
        const val NEXT = "NEXT"
    }

}
