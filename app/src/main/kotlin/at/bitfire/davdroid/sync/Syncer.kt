/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.sync

import android.accounts.Account
import android.content.ContentProviderClient
import android.content.Context
import android.content.SyncResult
import android.os.DeadObjectException
import android.provider.ContactsContract
import at.bitfire.davdroid.InvalidAccountException
import at.bitfire.davdroid.R
import at.bitfire.davdroid.db.AppDatabase
import at.bitfire.davdroid.log.Logger
import at.bitfire.davdroid.network.HttpClient
import at.bitfire.davdroid.settings.AccountSettings
import java.util.logging.Level

/**
 * Base class for sync code.
 *
 * Contains generic sync code, equal for all sync authorities, checks sync conditions and does
 * validation.
 *
 * Also provides useful methods that can be used by derived syncers ie [CalendarSyncer], etc.
 */
abstract class Syncer(
    val context: Context,
    val db: AppDatabase
) {

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


    abstract fun sync(account: Account, extras: Array<String>, authority: String, httpClient: Lazy<HttpClient>, provider: ContentProviderClient, syncResult: SyncResult)

    fun onPerformSync(
        account: Account,
        extras: Array<String>,
        authority: String,
        syncResult: SyncResult
    ) {
        Logger.log.log(Level.INFO, "$authority sync of $account initiated", extras.joinToString(", "))

        // use contacts provider for address books
        val contentAuthority =
            if (authority == context.getString(R.string.address_books_authority))
                ContactsContract.AUTHORITY
            else
                authority

        val accountSettings by lazy { AccountSettings(context, account) }
        val httpClient = lazy { HttpClient.Builder(context, accountSettings).build() }

        // acquire ContentProviderClient of authority to be synced
        val provider = try {
            context.contentResolver.acquireContentProviderClient(contentAuthority)
        } catch (e: SecurityException) {
            Logger.log.log(Level.WARNING, "Missing permissions for authority $contentAuthority", e)
            null
        }

        if (provider == null) {
            /* Can happen if
             - we're not allowed to access the content provider, or
             - the content provider is not available at all, for instance because the respective
               system app, like "contacts storage" is disabled */
            Logger.log.warning("Couldn't connect to content provider of authority $contentAuthority")
            syncResult.stats.numParseExceptions++ // hard sync error
            return
        }

        // run sync
        try {
            val runSync = /* ose */ true

            if (runSync)
                sync(account, extras, contentAuthority, httpClient, provider, syncResult)

        } catch (e: DeadObjectException) {
            /* May happen when the remote process dies or (since Android 14) when IPC (for instance with the calendar provider)
            is suddenly forbidden because our sync process was demoted from a "service process" to a "cached process". */
            Logger.log.log(Level.WARNING, "Received DeadObjectException, treating as soft error", e)
            syncResult.stats.numIoExceptions++

        } catch (e: InvalidAccountException) {
            Logger.log.log(Level.WARNING, "Account was removed during synchronization", e)

        } catch (e: Exception) {
            Logger.log.log(Level.SEVERE, "Couldn't sync $contentAuthority", e)
            syncResult.stats.numParseExceptions++ // Hard sync error

        } finally {
            if (httpClient.isInitialized())
                httpClient.value.close()
            Logger.log.log(
                Level.INFO,
                "$contentAuthority sync of $account finished",
                extras.joinToString(", "))
        }
    }

}