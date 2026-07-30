/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */
package at.bitfire.davdroid.sync

import at.bitfire.synctools.vcard.GroupMethod

/**
 * Immutable snapshot of the [at.bitfire.davdroid.settings.AccountSettings] that are relevant for a sync run.
 *
 * Should be created once at the start of a sync run instead of querying [at.bitfire.davdroid.settings.AccountSettings]
 * again and again during the same sync. This way, settings can't change in the middle of
 * a sync run, and expensive/blocking [at.bitfire.davdroid.settings.AccountSettings] access is reduced.
 */
data class SyncSettings(
    // AccountSettings (same order)
    val syncWifiOnly: Boolean,
    val syncWifiOnlySSIDs: List<String>?,
    val ignoreVpns: Boolean,
    val timeRangePastDays: Int?,
    val defaultAlarm: Int?,
    val eventColors: Boolean,
    val groupMethod: GroupMethod
)
