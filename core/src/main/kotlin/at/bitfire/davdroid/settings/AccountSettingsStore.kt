/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.settings

import at.bitfire.davdroid.accounts.AccountId
import at.bitfire.davdroid.sync.AutomaticSyncManager
import at.bitfire.davdroid.sync.SyncDataType
import at.bitfire.synctools.util.SensitiveString
import at.bitfire.synctools.util.trimToNull
import at.bitfire.synctools.vcard.GroupMethod
import net.openid.appauth.AuthState

interface AccountSettingsStore<IdType: AccountId> {

    val accountId: IdType

    val settingsManager: SettingsManager
    val automaticSyncManager: AutomaticSyncManager

    /**
     * Gets the value of the setting with the given key. Returns `null` if the setting is not set.
     */
    fun get(key: String): String?

    /**
     * Gets the value of a sensitive setting with the given key. Returns `null` if the setting is not set.
     */
    fun getSensitiveValue(key: String): SensitiveString?

    /**
     * Updates the value of the setting with [key] to [value].
     */
    fun set(key: String, value: String?)

    /**
     * Updates the value of the sensitive setting with [key] to [value].
     */
    fun setSensitiveValue(key: String, value: SensitiveString?)


    //<editor-fold desc="authentication settings">

    fun credentials() = Credentials(
        get(KEY_USERNAME),
        getSensitiveValue(KEY_PASSWORD),

        get(KEY_CERTIFICATE_ALIAS),

        get(KEY_AUTH_STATE)?.let { json ->
            AuthState.jsonDeserialize(json)
        }
    )

    fun credentials(credentials: Credentials) {
        // Basic/Digest auth
        set(KEY_USERNAME, credentials.username)
        setSensitiveValue(KEY_PASSWORD, credentials.password)

        // client certificate
        set(KEY_CERTIFICATE_ALIAS, credentials.certificateAlias)

        // OAuth
        credentials.authState?.let { authState ->
            updateAuthState(authState)
        }
    }

    fun updateAuthState(authState: AuthState) {
        set(KEY_AUTH_STATE, authState.jsonSerializeString())
    }

    /**
     * Returns whether users can modify credentials from the account settings screen.
     */
    fun changingCredentialsAllowed(): Boolean {
        val credentialsLock = settingsManager.getIntOrNull(CREDENTIALS_LOCK)
        return credentialsLock == null || credentialsLock != CREDENTIALS_LOCK_AT_LOGIN_AND_SETTINGS
    }

    //</editor-fold>

    //<editor-fold desc="sync settings">

    /**
     * Gets the currently set sync interval for this account and data type in seconds.
     *
     * @param dataType  data type of desired sync interval
     * @return sync interval in seconds, or `null` if not set (not applicable or only manual sync)
     */
    fun getSyncInterval(dataType: SyncDataType): Long? {
        val key = when (dataType) {
            SyncDataType.CONTACTS -> KEY_SYNC_INTERVAL_ADDRESSBOOKS
            SyncDataType.EVENTS -> KEY_SYNC_INTERVAL_CALENDARS
            SyncDataType.TASKS -> KEY_SYNC_INTERVAL_TASKS
        }
        return when (val seconds = get(key)?.toLong()) {
            null -> settingsManager.getLongOrNull(Settings.DEFAULT_SYNC_INTERVAL)   // no setting → default value
            SYNC_INTERVAL_MANUALLY -> null      // manual sync
            else -> seconds
        }
    }

    /**
     * Sets the sync interval for the given data type and updates the automatic sync.
     *
     * @param dataType              data type of the sync interval to set
     * @param seconds               sync interval in seconds; _null_ for no periodic sync
     */
    fun setSyncInterval(dataType: SyncDataType, seconds: Long?) {
        val key = when (dataType) {
            SyncDataType.CONTACTS -> KEY_SYNC_INTERVAL_ADDRESSBOOKS
            SyncDataType.EVENTS -> KEY_SYNC_INTERVAL_CALENDARS
            SyncDataType.TASKS -> KEY_SYNC_INTERVAL_TASKS
        }
        val newValue = seconds ?: SYNC_INTERVAL_MANUALLY
        set(key, newValue.toString())

        automaticSyncManager.updateAutomaticSync(accountId, dataType)
    }

    fun getSyncWifiOnly(): Boolean {
        return if (settingsManager.containsKey(KEY_WIFI_ONLY))
            settingsManager.getBoolean(KEY_WIFI_ONLY)
        else
            get(KEY_WIFI_ONLY) != null
    }

    fun setSyncWiFiOnly(wiFiOnly: Boolean) {
        set(KEY_WIFI_ONLY, if (wiFiOnly) "1" else null)
        automaticSyncManager.updateAutomaticSync(accountId)
    }

    fun getSyncWifiOnlySSIDs(): List<String>? {
        return if (getSyncWifiOnly()) {
            val strSsids = if (settingsManager.containsKey(KEY_WIFI_ONLY_SSIDS))
                settingsManager.getString(KEY_WIFI_ONLY_SSIDS)
            else
                get(KEY_WIFI_ONLY_SSIDS)
            strSsids?.split(',')
        } else
            null
    }

    fun setSyncWifiOnlySSIDs(ssids: List<String>?) {
        set(KEY_WIFI_ONLY_SSIDS, ssids?.joinToString(",").trimToNull())
    }

    fun getIgnoreVpns(): Boolean {
        return when (get(KEY_IGNORE_VPNS)) {
            null -> settingsManager.getBoolean(KEY_IGNORE_VPNS)
            "0" -> false
            else -> true
        }
    }

    fun setIgnoreVpns(ignoreVpns: Boolean) {
        set(KEY_IGNORE_VPNS, if (ignoreVpns) "1" else "0")
    }

    //</editor-fold>

    //<editor-fold desc="CalDAV settings">

    fun getTimeRangePastDays(): Int? {
        val strDays = get(KEY_TIME_RANGE_PAST_DAYS)
        return if (strDays != null) {
            val days = strDays.toInt()
            if (days < 0)
                null
            else
                days
        } else
            DEFAULT_TIME_RANGE_PAST_DAYS
    }

    fun setTimeRangePastDays(days: Int?) {
        set(KEY_TIME_RANGE_PAST_DAYS, (days ?: -1).toString())
    }

    /**
     * Takes the default alarm setting (in this order) from
     *
     * 1. the local account settings
     * 2. the settings provider (unless the value is -1 there).
     *
     * @return A default reminder shall be created this number of minutes before the start of every
     * non-full-day event without reminder. *null*: No default reminders shall be created.
     */
    fun getDefaultAlarm(): Int? {
        return get(KEY_DEFAULT_ALARM)?.toInt() ?: settingsManager.getIntOrNull(KEY_DEFAULT_ALARM)?.takeIf { it != -1 }
    }

    /**
     * Sets the default alarm value in the local account settings, if the new value differs
     * from the value of the settings provider. If the new value is the same as the value of
     * the settings provider, the local setting will be deleted, so that the settings provider
     * value applies.
     *
     * @param minBefore The number of minutes a default reminder shall be created before the
     * start of every non-full-day event without reminder. *null*: No default reminders shall be created.
     */
    fun setDefaultAlarm(minBefore: Int?) {
        set(
            KEY_DEFAULT_ALARM,
            if (minBefore == settingsManager.getIntOrNull(KEY_DEFAULT_ALARM)?.takeIf { it != -1 })
                null
            else
                minBefore?.toString()
        )
    }

    fun getManageCalendarColors(): Boolean {
        return if (settingsManager.containsKey(KEY_MANAGE_CALENDAR_COLORS))
            settingsManager.getBoolean(KEY_MANAGE_CALENDAR_COLORS)
        else
            get(KEY_MANAGE_CALENDAR_COLORS) == null
    }

    fun setManageCalendarColors(manage: Boolean) {
        set(KEY_MANAGE_CALENDAR_COLORS, if (manage) null else "0")
    }

    fun getEventColors(): Boolean {
        return if (settingsManager.containsKey(KEY_EVENT_COLORS))
            settingsManager.getBoolean(KEY_EVENT_COLORS)
        else
            get(KEY_EVENT_COLORS) != null
    }

    fun setEventColors(useColors: Boolean) {
        set(KEY_EVENT_COLORS, if (useColors) "1" else null)
    }

    //</editor-fold>

    //<editor-fold desc="CardDAV settings">

    fun getGroupMethod(): GroupMethod {
        val name = settingsManager.getString(KEY_CONTACT_GROUP_METHOD) ?: get(KEY_CONTACT_GROUP_METHOD)
        if (name != null)
            try {
                return GroupMethod.valueOf(name)
            } catch (_: IllegalArgumentException) {
            }
        return GroupMethod.GROUP_VCARDS
    }

    fun setGroupMethod(method: GroupMethod) {
        set(KEY_CONTACT_GROUP_METHOD, method.name)
    }

    //</editor-fold>

    //<editor-fold desc="UI settings">

    /**
     * Whether to show only personal collections in the UI
     *
     * @return *true* if only personal collections shall be shown; *false* otherwise
     */
    fun getShowOnlyPersonal(): Boolean {
        return when (settingsManager.getIntOrNull(KEY_SHOW_ONLY_PERSONAL)) {
            0 -> false
            1 -> true
            else /* including -1 */ -> get(KEY_SHOW_ONLY_PERSONAL) != null
        }
    }

    /**
     * Whether the user shall be able to change the setting (= setting not locked)
     *
     * @return *true* if the setting is locked; *false* otherwise
     */
    fun getShowOnlyPersonalLocked(): Boolean {
        return when (settingsManager.getIntOrNull(KEY_SHOW_ONLY_PERSONAL)) {
            0, 1 -> true
            else /* including -1 */ -> false
        }
    }

    fun setShowOnlyPersonal(showOnlyPersonal: Boolean) {
        set(KEY_SHOW_ONLY_PERSONAL, if (showOnlyPersonal) "1" else null)
    }

    //</editor-fold>

    companion object {

        const val KEY_SYNC_INTERVAL_ADDRESSBOOKS = "sync_interval_addressbooks"
        const val KEY_SYNC_INTERVAL_CALENDARS = "sync_interval_calendars"

        /** Stores the tasks sync interval (in seconds) so that it can be set again when the provider is switched */
        const val KEY_SYNC_INTERVAL_TASKS = "sync_interval_tasks"

        const val KEY_USERNAME = "user_name"
        const val KEY_PASSWORD = "password"
        const val KEY_CERTIFICATE_ALIAS = "certificate_alias"

        const val CREDENTIALS_LOCK = "login_credentials_lock"
        const val CREDENTIALS_LOCK_NO_LOCK = 0
        const val CREDENTIALS_LOCK_AT_LOGIN = 1
        const val CREDENTIALS_LOCK_AT_LOGIN_AND_SETTINGS = 2

        /** OAuth [AuthState] (serialized as JSON) */
        const val KEY_AUTH_STATE = "auth_state"

        const val KEY_PRECONFIGURATION_URL = "preconfiguration_url"

        const val KEY_WIFI_ONLY = "wifi_only"               // sync on WiFi only (default: false)
        const val KEY_WIFI_ONLY_SSIDS = "wifi_only_ssids"   // restrict sync to specific WiFi SSIDs
        const val KEY_IGNORE_VPNS = "ignore_vpns"           // ignore vpns at connection detection

        /** Time range limitation to the past [in days]. Values:
         *
         * - null: default value (DEFAULT_TIME_RANGE_PAST_DAYS)
         * - <0 (typically -1): no limit
         * - n>0: entries more than n days in the past won't be synchronized
         */
        const val KEY_TIME_RANGE_PAST_DAYS = "time_range_past_days"
        const val DEFAULT_TIME_RANGE_PAST_DAYS = 90

        /**
         * Whether a default alarm shall be assigned to received events/tasks which don't have an alarm.
         * Value can be null (no default alarm) or an integer (default alarm shall be created this
         * number of minutes before the event/task).
         */
        const val KEY_DEFAULT_ALARM = "default_alarm"

        /** Whether DAVx5 sets the local calendar color to the value from service DB at every sync
        value = *null* (not existing): true (default);
        "0"                    false */
        const val KEY_MANAGE_CALENDAR_COLORS = "manage_calendar_colors"

        /** Whether DAVx5 populates and uses CalendarContract.Colors
        value = *null* (not existing)   false (default);
        "1"                     true */
        const val KEY_EVENT_COLORS = "event_colors"

        /** Contact group method:
         *null (not existing)*     groups as separate vCards (default);
        "CATEGORIES"              groups are per-contact CATEGORIES
         */
        const val KEY_CONTACT_GROUP_METHOD = "contact_group_method"

        /** UI preference: Show only personal collections
        value = *null* (not existing)   show all collections (default);
        "1"                             show only personal collections */
        const val KEY_SHOW_ONLY_PERSONAL = "show_only_personal"

        internal const val SYNC_INTERVAL_MANUALLY = -1L
    }

}
