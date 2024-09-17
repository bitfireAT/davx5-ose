/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */
package at.bitfire.davdroid.settings

import android.accounts.Account
import android.accounts.AccountManager
import android.content.ContentResolver
import android.content.Context
import android.os.Bundle
import android.provider.CalendarContract
import androidx.annotation.WorkerThread
import at.bitfire.davdroid.InvalidAccountException
import at.bitfire.davdroid.R
import at.bitfire.davdroid.db.Credentials
import at.bitfire.davdroid.sync.SyncUtils
import at.bitfire.davdroid.sync.worker.PeriodicSyncWorker
import at.bitfire.davdroid.util.setAndVerifyUserData
import at.bitfire.davdroid.util.trimToNull
import at.bitfire.ical4android.TaskProvider
import at.bitfire.vcard4android.GroupMethod
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import net.openid.appauth.AuthState
import java.util.logging.Level
import java.util.logging.Logger

/**
 * Manages settings of an account.
 *
 * @param account   account to take settings from
 *
 * @throws InvalidAccountException      on construction when the account doesn't exist (anymore)
 * @throws IllegalArgumentException     when the account is not a DAVx5 account
 */
class AccountSettings @AssistedInject constructor(
    @Assisted val account: Account,
    @ApplicationContext val context: Context,
    private val logger: Logger,
    private val migrationsFactory: AccountSettingsMigrations.Factory,
    private val settingsManager: SettingsManager,
) {

    @AssistedFactory
    interface Factory {
        fun create(account: Account): AccountSettings
    }

    companion object {

        const val CURRENT_VERSION = 16
        const val KEY_SETTINGS_VERSION = "version"

        const val KEY_SYNC_INTERVAL_ADDRESSBOOKS = "sync_interval_addressbooks"
        const val KEY_SYNC_INTERVAL_CALENDARS = "sync_interval_calendars"

        /** Stores the tasks sync interval (in seconds) so that it can be set again when the provider is switched */
        const val KEY_SYNC_INTERVAL_TASKS = "sync_interval_tasks"

        const val KEY_USERNAME = "user_name"
        const val KEY_CERTIFICATE_ALIAS = "certificate_alias"

        /** OAuth [AuthState] (serialized as JSON) */
        const val KEY_AUTH_STATE = "auth_state"

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

        const val SYNC_INTERVAL_MANUALLY = -1L

        /** Static property to indicate whether AccountSettings migration is currently running.
         * **Access must be `synchronized` with `AccountSettings::class.java`.** */
        @Volatile
        var currentlyUpdating = false

        fun initialUserData(credentials: Credentials?): Bundle {
            val bundle = Bundle()
            bundle.putString(KEY_SETTINGS_VERSION, CURRENT_VERSION.toString())

            if (credentials != null) {
                if (credentials.username != null)
                    bundle.putString(KEY_USERNAME, credentials.username)

                if (credentials.certificateAlias != null)
                    bundle.putString(KEY_CERTIFICATE_ALIAS, credentials.certificateAlias)

                if (credentials.authState != null)
                    bundle.putString(KEY_AUTH_STATE, credentials.authState.jsonSerializeString())
            }

            return bundle
        }

    }


    val accountManager: AccountManager = AccountManager.get(context)
    init {
        // synchronize because account migration must only be run one time
        synchronized(AccountSettings::class.java) {
            val versionStr = accountManager.getUserData(account, KEY_SETTINGS_VERSION) ?: throw InvalidAccountException(account)
            var version = 0
            try {
                version = Integer.parseInt(versionStr)
            } catch (e: NumberFormatException) {
                logger.log(Level.SEVERE, "Invalid account version: $versionStr", e)
            }
            logger.fine("Account ${account.name} has version $version, current version: $CURRENT_VERSION")

            if (version < CURRENT_VERSION) {
                if (currentlyUpdating) {
                    logger.severe("Redundant call: migration created AccountSettings(). This must never happen.")
                    throw IllegalStateException("Redundant call: migration created AccountSettings()")
                } else {
                    currentlyUpdating = true
                    update(version)
                    currentlyUpdating = false
                }
            }
        }
    }


    // authentication settings

    fun credentials() = Credentials(
        accountManager.getUserData(account, KEY_USERNAME),
        accountManager.getPassword(account),

        accountManager.getUserData(account, KEY_CERTIFICATE_ALIAS),

        accountManager.getUserData(account, KEY_AUTH_STATE)?.let { json ->
            AuthState.jsonDeserialize(json)
        }
    )

    fun credentials(credentials: Credentials) {
        // Basic/Digest auth
        accountManager.setAndVerifyUserData(account, KEY_USERNAME, credentials.username)
        accountManager.setPassword(account, credentials.password)

        // client certificate
        accountManager.setAndVerifyUserData(account, KEY_CERTIFICATE_ALIAS, credentials.certificateAlias)

        // OAuth
        accountManager.setAndVerifyUserData(account, KEY_AUTH_STATE, credentials.authState?.jsonSerializeString())
    }


    // sync. settings

    /**
     * Gets the currently set sync interval for this account in seconds.
     *
     * @param authority authority to check (for instance: [CalendarContract.AUTHORITY]])
     * @return sync interval in seconds; *[SYNC_INTERVAL_MANUALLY]* if manual sync; *null* if not set
     */
    fun getSyncInterval(authority: String): Long? {
        val addrBookAuthority = context.getString(R.string.address_books_authority)

        if (ContentResolver.getIsSyncable(account, authority) <= 0 && authority != addrBookAuthority)
            return null

        val key = when {
            authority == addrBookAuthority ->
                KEY_SYNC_INTERVAL_ADDRESSBOOKS
            authority == CalendarContract.AUTHORITY ->
                KEY_SYNC_INTERVAL_CALENDARS
            TaskProvider.ProviderName.entries.any { it.authority == authority } ->
                KEY_SYNC_INTERVAL_TASKS
            else -> return null
        }
        return accountManager.getUserData(account, key)?.toLong()
    }

    fun getTasksSyncInterval() = accountManager.getUserData(account, KEY_SYNC_INTERVAL_TASKS)?.toLong()

    /**
     * Sets the sync interval and en- or disables periodic sync for the given account and authority.
     * Does *not* call [ContentResolver.setIsSyncable].
     *
     * This method blocks until a worker as been created and enqueued (sync active) or removed
     * (sync disabled), so it should not be called from the UI thread.
     *
     * @param authority sync authority (like [CalendarContract.AUTHORITY])
     * @param _seconds if [SYNC_INTERVAL_MANUALLY]: automatic sync will be disabled;
     * otherwise (must be ≥ 15 min): automatic sync will be enabled and set to the given number of seconds
     */
    @WorkerThread
    fun setSyncInterval(authority: String, _seconds: Long) {
        val seconds =
            if (_seconds != SYNC_INTERVAL_MANUALLY && _seconds < 60*15)
                60*15
            else
                _seconds

        // Store (user defined) sync interval in account settings
        val key = when {
            authority == context.getString(R.string.address_books_authority) ->
                KEY_SYNC_INTERVAL_ADDRESSBOOKS
            authority == CalendarContract.AUTHORITY ->
                KEY_SYNC_INTERVAL_CALENDARS
            TaskProvider.ProviderName.entries.any { it.authority == authority } ->
                KEY_SYNC_INTERVAL_TASKS
            else -> {
                logger.warning("Sync interval not applicable to authority $authority")
                return
            }
        }
        accountManager.setAndVerifyUserData(account, key, seconds.toString())

        // update sync workers (needs already updated sync interval in AccountSettings)
        updatePeriodicSyncWorker(authority, seconds, getSyncWifiOnly())

        // Also enable/disable content change triggered syncs (SyncFramework automatic sync).
        // We could make this a separate user adjustable setting later on.
        setSyncOnContentChange(authority, seconds != SYNC_INTERVAL_MANUALLY)
    }

    /**
     * Enables/disables sync adapter automatic sync (content triggered sync) for the given
     * account and authority. Does *not* call [ContentResolver.setIsSyncable].
     *
     * We use the sync adapter framework only for the trigger, actual syncing is implemented
     * with WorkManager. The trigger comes in through SyncAdapterService.
     *
     * This method blocks until the sync-on-content-change has been enabled or disabled, so it
     * should not be called from the UI thread.
     *
     * @param enable    *true* enables automatic sync; *false* disables it
     * @param authority sync authority (like [CalendarContract.AUTHORITY])
     * @return whether the content triggered sync was enabled successfully
     */
    @WorkerThread
    fun setSyncOnContentChange(authority: String, enable: Boolean): Boolean {
        // Enable content change triggers (sync adapter framework)
        val setContentTrigger: () -> Boolean =
            /* Ugly hack: because there is no callback for when the sync status/interval has been
            updated, we need to make this call blocking. */
            if (enable) {{
                logger.fine("Enabling content-triggered sync of $account/$authority")
                ContentResolver.setSyncAutomatically(account, authority, true) // enables content triggers
                // Remove unwanted sync framework periodic syncs created by setSyncAutomatically
                for (periodicSync in ContentResolver.getPeriodicSyncs(account, authority))
                    ContentResolver.removePeriodicSync(periodicSync.account, periodicSync.authority, periodicSync.extras)
                /* return */ ContentResolver.getSyncAutomatically(account, authority)
            }} else {{
                logger.fine("Disabling content-triggered sync of $account/$authority")
                ContentResolver.setSyncAutomatically(account, authority, false) // disables content triggers
                /* return */ !ContentResolver.getSyncAutomatically(account, authority)
            }}

        // try up to 10 times with 100 ms pause
        for (idxTry in 0 until 10) {
            if (setContentTrigger())
                // successfully set
                return true
            Thread.sleep(100)
        }
        return false
    }

    fun getSyncWifiOnly() =
        if (settingsManager.containsKey(KEY_WIFI_ONLY))
            settingsManager.getBoolean(KEY_WIFI_ONLY)
        else
            accountManager.getUserData(account, KEY_WIFI_ONLY) != null

    fun setSyncWiFiOnly(wiFiOnly: Boolean) {
        accountManager.setAndVerifyUserData(account, KEY_WIFI_ONLY, if (wiFiOnly) "1" else null)

        // update sync workers (needs already updated wifi-only flag in AccountSettings)
        for (authority in SyncUtils.syncAuthorities(context))
            updatePeriodicSyncWorker(authority, getSyncInterval(authority), wiFiOnly)
    }

    fun getSyncWifiOnlySSIDs(): List<String>? =
        if (getSyncWifiOnly()) {
            val strSsids = if (settingsManager.containsKey(KEY_WIFI_ONLY_SSIDS))
                settingsManager.getString(KEY_WIFI_ONLY_SSIDS)
            else
                accountManager.getUserData(account, KEY_WIFI_ONLY_SSIDS)
            strSsids?.split(',')
        } else
            null
    fun setSyncWifiOnlySSIDs(ssids: List<String>?) =
        accountManager.setAndVerifyUserData(account, KEY_WIFI_ONLY_SSIDS, ssids?.joinToString(",").trimToNull())

    fun getIgnoreVpns(): Boolean =
        when (accountManager.getUserData(account, KEY_IGNORE_VPNS)) {
            null -> settingsManager.getBoolean(KEY_IGNORE_VPNS)
            "0" -> false
            else -> true
        }

    fun setIgnoreVpns(ignoreVpns: Boolean) =
        accountManager.setAndVerifyUserData(account, KEY_IGNORE_VPNS, if (ignoreVpns) "1" else "0")

    /**
     * Updates the periodic sync worker of an authority according to
     *
     * - the sync interval and
     * - the _Sync WiFi only_ flag.
     *
     * @param authority   periodic sync workers for this authority will be updated
     * @param seconds     sync interval in seconds (`null` or [SYNC_INTERVAL_MANUALLY] disables periodic sync)
     * @param wiFiOnly    sync Wifi only flag
     */
    fun updatePeriodicSyncWorker(authority: String, seconds: Long?, wiFiOnly: Boolean) {
        try {
            if (seconds == null || seconds == SYNC_INTERVAL_MANUALLY) {
                logger.fine("Disabling periodic sync of $account/$authority")
                PeriodicSyncWorker.disable(context, account, authority)
            } else {
                logger.fine("Setting periodic sync of $account/$authority to $seconds seconds (wifiOnly=$wiFiOnly)")
                PeriodicSyncWorker.enable(context, account, authority, seconds, wiFiOnly)
            }.result.get() // On operation (enable/disable) failure exception is thrown
        } catch (e: Exception) {
            logger.log(Level.SEVERE, "Failed to set sync interval of $account/$authority to $seconds seconds", e)
        }
    }


    // CalDAV settings

    fun getTimeRangePastDays(): Int? {
        val strDays = accountManager.getUserData(account, KEY_TIME_RANGE_PAST_DAYS)
        return if (strDays != null) {
            val days = strDays.toInt()
            if (days < 0)
                null
            else
                days
        } else
            DEFAULT_TIME_RANGE_PAST_DAYS
    }

    fun setTimeRangePastDays(days: Int?) =
            accountManager.setAndVerifyUserData(account, KEY_TIME_RANGE_PAST_DAYS, (days ?: -1).toString())

    /**
     * Takes the default alarm setting (in this order) from
     *
     * 1. the local account settings
     * 2. the settings provider (unless the value is -1 there).
     *
     * @return A default reminder shall be created this number of minutes before the start of every
     * non-full-day event without reminder. *null*: No default reminders shall be created.
     */
    fun getDefaultAlarm() =
            accountManager.getUserData(account, KEY_DEFAULT_ALARM)?.toInt() ?:
            settingsManager.getIntOrNull(KEY_DEFAULT_ALARM)?.takeIf { it != -1 }

    /**
     * Sets the default alarm value in the local account settings, if the new value differs
     * from the value of the settings provider. If the new value is the same as the value of
     * the settings provider, the local setting will be deleted, so that the settings provider
     * value applies.
     *
     * @param minBefore The number of minutes a default reminder shall be created before the
     * start of every non-full-day event without reminder. *null*: No default reminders shall be created.
     */
    fun setDefaultAlarm(minBefore: Int?) =
            accountManager.setAndVerifyUserData(account, KEY_DEFAULT_ALARM,
                    if (minBefore == settingsManager.getIntOrNull(KEY_DEFAULT_ALARM)?.takeIf { it != -1 })
                        null
                    else
                        minBefore?.toString())

    fun getManageCalendarColors() =
        if (settingsManager.containsKey(KEY_MANAGE_CALENDAR_COLORS))
            settingsManager.getBoolean(KEY_MANAGE_CALENDAR_COLORS)
        else
            accountManager.getUserData(account, KEY_MANAGE_CALENDAR_COLORS) == null
    fun setManageCalendarColors(manage: Boolean) =
            accountManager.setAndVerifyUserData(account, KEY_MANAGE_CALENDAR_COLORS, if (manage) null else "0")

    fun getEventColors() =
        if (settingsManager.containsKey(KEY_EVENT_COLORS))
            settingsManager.getBoolean(KEY_EVENT_COLORS)
        else
            accountManager.getUserData(account, KEY_EVENT_COLORS) != null
    fun setEventColors(useColors: Boolean) =
            accountManager.setAndVerifyUserData(account, KEY_EVENT_COLORS, if (useColors) "1" else null)

    // CardDAV settings

    fun getGroupMethod(): GroupMethod {
        val name = settingsManager.getString(KEY_CONTACT_GROUP_METHOD) ?:
                accountManager.getUserData(account, KEY_CONTACT_GROUP_METHOD)
        if (name != null)
            try {
                return GroupMethod.valueOf(name)
            }
            catch (_: IllegalArgumentException) {
            }
        return GroupMethod.GROUP_VCARDS
    }

    fun setGroupMethod(method: GroupMethod) {
        accountManager.setAndVerifyUserData(account, KEY_CONTACT_GROUP_METHOD, method.name)
    }


    // UI settings

    data class ShowOnlyPersonal(
        val onlyPersonal: Boolean,
        val locked: Boolean
    )

    fun getShowOnlyPersonal(): ShowOnlyPersonal {
        @Suppress("DEPRECATION")
        val pair = getShowOnlyPersonalPair()
        return ShowOnlyPersonal(onlyPersonal = pair.first, locked = !pair.second)
    }

    /**
     * Whether only personal collections should be shown.
     *
     * @return [Pair] of values:
     *
     *   1. (first) whether only personal collections should be shown
     *   2. (second) whether the user shall be able to change the setting (= setting not locked)
     */
    @Deprecated("Use getShowOnlyPersonal() instead", replaceWith = ReplaceWith("getShowOnlyPersonal()"))
    fun getShowOnlyPersonalPair(): Pair<Boolean, Boolean> =
            when (settingsManager.getIntOrNull(KEY_SHOW_ONLY_PERSONAL)) {
                0 -> Pair(false, false)
                1 -> Pair(true, false)
                else /* including -1 */ -> Pair(accountManager.getUserData(account, KEY_SHOW_ONLY_PERSONAL) != null, true)
            }

    fun setShowOnlyPersonal(showOnlyPersonal: Boolean) {
        accountManager.setAndVerifyUserData(account, KEY_SHOW_ONLY_PERSONAL, if (showOnlyPersonal) "1" else null)
    }


    // update from previous account settings

    private fun update(baseVersion: Int) {
        for (toVersion in baseVersion+1 ..CURRENT_VERSION) {
            val fromVersion = toVersion-1
            logger.info("Updating account ${account.name} from version $fromVersion to $toVersion")
            try {
                val migrations = migrationsFactory.create(
                    account = account,
                    accountSettings = this
                )
                val updateProc = AccountSettingsMigrations::class.java.getDeclaredMethod("update_${fromVersion}_$toVersion")
                updateProc.invoke(migrations)

                logger.info("Account version update successful")
                accountManager.setAndVerifyUserData(account, KEY_SETTINGS_VERSION, toVersion.toString())
            } catch (e: Exception) {
                logger.log(Level.SEVERE, "Couldn't update account settings", e)
            }
        }
    }

}