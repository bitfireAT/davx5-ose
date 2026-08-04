/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.tasks.contract

import android.net.Uri
import android.provider.BaseColumns

/**
 * Table of task lists ("collections" in CalDAV terminology).
 *
 * Column source legend: `SYNC` = DAVx⁵ sync-local, no iCalendar counterpart ·
 * `PROV` = provider bookkeeping, no iCalendar counterpart.
 */
object TaskLists {

    const val PATH = "task_lists"

    fun contentUri(authority: String): Uri = TasksContract.contentUri(authority, PATH)

    /** `PROV` */
    const val _ID = BaseColumns._ID

    /** `SYNC` — Android account name this list is bound to. */
    const val ACCOUNT_NAME = "account_name"

    /** `SYNC` — Android account type this list is bound to. */
    const val ACCOUNT_TYPE = "account_type"

    /** `SYNC` — DAVx⁵ `Collection.id`. */
    const val _SYNC_ID = "_sync_id"

    /** `SYNC` — serialized `SyncState`. */
    const val SYNC_VERSION = "sync_version"

    /** `SYNC` — sync-adapter private. */
    const val SYNC1 = "sync1"
    /** `SYNC` — sync-adapter private. */
    const val SYNC2 = "sync2"
    /** `SYNC` — sync-adapter private. */
    const val SYNC3 = "sync3"
    /** `SYNC` — sync-adapter private. */
    const val SYNC4 = "sync4"

    /** RFC 4791 `displayname`. */
    const val LIST_NAME = "list_name"

    /** RFC 4791 `calendar-description`. */
    const val LIST_DESCRIPTION = "list_description"

    /** Apple `calendar-color` / RFC 7986 §5.9. */
    const val LIST_COLOR = "list_color"

    /** RFC 4791 / RFC 3744 `owner`. */
    const val LIST_OWNER = "list_owner"

    /** RFC 3744 ACL, derived from the collection's write privilege / forced read-only state. */
    const val ACCESS_LEVEL = "access_level"

    /** `PROV` — user/UI visibility state. Type: [Int], `0`/`1`. */
    const val VISIBLE = "visible"

    /** `PROV` — user/UI sync-enabled state. Type: [Int], `0`/`1`. */
    const val SYNC_ENABLED = "sync_enabled"

    /**
     * RFC 4791 `supported-calendar-component-set`. `VTODO` only for v1.
     */
    const val SUPPORTED_COMPONENTS = "supported_components"


    object AccessLevel {
        const val READ_ONLY = 0
        const val READ_WRITE = 1
    }

    object Component {
        const val VTODO = "VTODO"
    }

}
