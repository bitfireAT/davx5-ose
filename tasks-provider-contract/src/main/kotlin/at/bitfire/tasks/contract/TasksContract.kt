/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.tasks.contract

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri

/**
 * Public contract of the DAVx⁵ tasks provider (a DAVx⁵-hosted [android.content.ContentProvider]
 * for VTODO data, see `doc/tasks-provider.md` in the davx5-ose repository).
 *
 * This module has **no dependency on DAVx⁵ itself** (no Room, no sync engine, no app code) so
 * that third-party frontend apps can depend on it without pulling in anything DAVx⁵-internal —
 * exactly the way [org.dmfs.tasks.contract.TaskContract] works for OpenTasks.
 *
 * Every column, mimetype and enum value defined by this contract (and its sibling objects
 * [TaskLists], [Tasks], [TaskProperties], [TaskAlarms], [TaskInstances]) MUST cite a normative
 * source (an RFC 5545(-family) section, or an explicit "sync-local"/"provider" marker) — see
 * §0 of the design doc. No unattributed columns.
 */
object TasksContract {

    /**
     * Because a fixed authority would mean only one DAVx⁵ variant (OSE, Managed, Select, ...)
     * could be installed at a time, the authority of the tasks provider is **not fixed**. Every
     * installed provider implementation advertises itself with an `intent-filter` for this
     * action; frontends discover the authority at runtime via
     * [PackageManager.queryIntentContentProviders].
     */
    const val ACTION_TASKS_PROVIDER = "at.bitfire.tasks.action.TASKS_PROVIDER"

    /**
     * Permission required to read task data. Vendor-neutral (not `...davdroid...`) so that a
     * future non-DAVx⁵ implementation of this contract could serve the same frontends.
     */
    const val PERMISSION_READ = "at.bitfire.tasks.permission.READ"

    /**
     * Permission required to write task data.
     */
    const val PERMISSION_WRITE = "at.bitfire.tasks.permission.WRITE"

    /**
     * Query parameter to signal that the caller is the sync adapter for the account given by
     * [PARAM_ACCOUNT_NAME]/[PARAM_ACCOUNT_TYPE]. Controls tombstone-vs-real-delete semantics and
     * suppresses the `_dirty` flag being set on write.
     */
    const val PARAM_CALLER_IS_SYNCADAPTER = "caller_is_syncadapter"

    /**
     * Query parameter to submit the account name of the account a sync-adapter operation
     * applies to. Only meaningful together with [PARAM_CALLER_IS_SYNCADAPTER].
     */
    const val PARAM_ACCOUNT_NAME = "account_name"

    /**
     * Query parameter to submit the account type of the account a sync-adapter operation
     * applies to. Only meaningful together with [PARAM_CALLER_IS_SYNCADAPTER].
     */
    const val PARAM_ACCOUNT_TYPE = "account_type"

    /**
     * Discovers the authority of the (first) installed tasks provider implementing this
     * contract.
     *
     * @return authority of an installed tasks provider, or `null` if none is installed
     */
    fun discoverAuthority(context: Context): String? =
        context.packageManager
            .queryIntentContentProviders(Intent(ACTION_TASKS_PROVIDER), 0)
            .firstOrNull()
            ?.providerInfo
            ?.authority

    /**
     * Builds the base content URI for a given authority and table path.
     */
    fun contentUri(authority: String, path: String): Uri =
        Uri.Builder()
            .scheme("content")
            .authority(authority)
            .appendPath(path)
            .build()

}
