/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */
package at.bitfire.davdroid.sync

import at.bitfire.synctools.vcard.GroupMethod

object SyncSettingsFixtures {

    fun default() = SyncSettings(
        syncWifiOnly = false,
        syncWifiOnlySSIDs = null,
        ignoreVpns = true,
        timeRangePastDays = 90,
        defaultAlarm = null,
        eventColors = false,
        groupMethod = GroupMethod.GROUP_VCARDS
    )

}
