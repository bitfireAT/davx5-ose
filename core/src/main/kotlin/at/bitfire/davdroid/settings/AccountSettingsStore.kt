/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.settings

import at.bitfire.davdroid.sync.SyncDataType
import at.bitfire.synctools.vcard.GroupMethod
import net.openid.appauth.AuthState

interface AccountSettingsStore {

    //<editor-fold desc="authentication settings">

    fun credentials(): Credentials

    fun credentials(credentials: Credentials)

    fun updateAuthState(authState: AuthState)

    //</editor-fold>

    //<editor-fold desc="sync settings">

    /**
     * Gets the currently set sync interval for this account and data type in seconds.
     *
     * @param dataType  data type of desired sync interval
     * @return sync interval in seconds, or `null` if not set (not applicable or only manual sync)
     */
    fun getSyncInterval(dataType: SyncDataType): Long?

    /**
     * Sets the sync interval for the given data type and updates the automatic sync.
     *
     * @param dataType              data type of the sync interval to set
     * @param seconds               sync interval in seconds; _null_ for no periodic sync
     */
    fun setSyncInterval(dataType: SyncDataType, seconds: Long?)

    fun getSyncWifiOnly(): Boolean?

    fun setSyncWiFiOnly(wiFiOnly: Boolean)

    fun getSyncWifiOnlySSIDs(): List<String>?

    fun setSyncWifiOnlySSIDs(ssids: List<String>?)

    fun getIgnoreVpns(): Boolean

    fun setIgnoreVpns(ignoreVpns: Boolean)

    //</editor-fold>

    //<editor-fold desc="CalDAV settings">

    fun getTimeRangePastDays(): Int?

    fun setTimeRangePastDays(days: Int?)

    /**
     * Takes the default alarm setting (in this order) from
     *
     * 1. the local account settings
     * 2. the settings provider (unless the value is -1 there).
     *
     * @return A default reminder shall be created this number of minutes before the start of every
     * non-full-day event without reminder. *null*: No default reminders shall be created.
     */
    fun getDefaultAlarm(): Int?

    /**
     * Sets the default alarm value in the local account settings, if the new value differs
     * from the value of the settings provider. If the new value is the same as the value of
     * the settings provider, the local setting will be deleted, so that the settings provider
     * value applies.
     *
     * @param minBefore The number of minutes a default reminder shall be created before the
     * start of every non-full-day event without reminder. *null*: No default reminders shall be created.
     */
    fun setDefaultAlarm(minBefore: Int?)

    fun getManageCalendarColors(): Boolean?

    fun setManageCalendarColors(manage: Boolean)

    fun getEventColors(): Boolean?

    fun setEventColors(useColors: Boolean)

    //</editor-fold>

    //<editor-fold desc="CardDAV settings">

    fun getGroupMethod(): GroupMethod

    fun setGroupMethod(method: GroupMethod)

    //</editor-fold>

    //<editor-fold desc="UI settings">

    /**
     * Whether to show only personal collections in the UI
     *
     * @return *true* if only personal collections shall be shown; *false* otherwise
     */
    fun getShowOnlyPersonal(): Boolean

    /**
     * Whether the user shall be able to change the setting (= setting not locked)
     *
     * @return *true* if the setting is locked; *false* otherwise
     */
    fun getShowOnlyPersonalLocked(): Boolean

    fun setShowOnlyPersonal(showOnlyPersonal: Boolean)

    //</editor-fold>

}
