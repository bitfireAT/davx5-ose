/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.davdroid.syncadapter

import android.accounts.Account
import android.content.ContentProviderClient
import android.content.Context
import android.content.SyncResult
import at.bitfire.davdroid.network.HttpClient
import at.bitfire.davdroid.InvalidAccountException
import at.bitfire.davdroid.db.AppDatabase
import at.bitfire.davdroid.log.Logger
import at.bitfire.davdroid.settings.AccountSettings
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.util.*
import java.util.logging.Level

/**
 * Base class for sync code.
 *
 * Contains generic sync code, equal for all sync authorities, checks sync conditions and does
 * validation.
 *
 * Also provides useful methods that can be used by derived syncers ie [CalendarSyncer], etc.
 */
abstract class Syncer(val context: Context) {

    companion object {

        /**
         * Requests a re-synchronization of all entries. For instance, if this extra is
         * set for a calendar sync, all remote events will be listed and checked for remote
         * changes again.
         *
         * Useful if settings which modify the remote resource list (like the CalDAV setting
         * "sync events n days in the past") have been changed.
         */
        const val SYNC_EXTRAS_RESYNC = "resync"

        /**
         * Requests a full re-synchronization of all entries. For instance, if this extra is
         * set for an address book sync, all contacts will be downloaded again and updated in the
         * local storage.
         *
         * Useful if settings which modify parsing/local behavior have been changed.
         */
        const val SYNC_EXTRAS_FULL_RESYNC = "full_resync"

    }

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface SyncAdapterEntryPoint {
        fun appDatabase(): AppDatabase
    }

    private val syncAdapterEntryPoint = EntryPointAccessors.fromApplication(context, SyncAdapterEntryPoint::class.java)
    internal val db = syncAdapterEntryPoint.appDatabase()


    abstract fun sync(account: Account, extras: Array<String>, authority: String, httpClient: Lazy<HttpClient>, provider: ContentProviderClient, syncResult: SyncResult)

    fun onPerformSync(
        account: Account,
        extras: Array<String>,
        authority: String,
        provider: ContentProviderClient,
        syncResult: SyncResult
    ) {
        Logger.log.log(Level.INFO, "$authority sync of $account initiated", extras.joinToString(", "))

        val accountSettings by lazy { AccountSettings(context, account) }
        val httpClient = lazy { HttpClient.Builder(context, accountSettings).build() }

        try {
            val runSync = true  /* ose */
            if (runSync)
                sync(account, extras, authority, httpClient, provider, syncResult)
        } catch (e: InvalidAccountException) {
            Logger.log.log(Level.WARNING, "Account was removed during synchronization", e)
        } finally {
            if (httpClient.isInitialized())
                httpClient.value.close()
            Logger.log.log(
                Level.INFO,
                "$authority sync of $account finished",
                extras.joinToString(", "))
        }
    }

}
