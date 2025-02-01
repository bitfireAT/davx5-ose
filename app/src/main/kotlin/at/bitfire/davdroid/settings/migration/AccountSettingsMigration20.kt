/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.settings.migration

import android.accounts.Account
import android.accounts.AccountManager
import android.content.Context
import android.provider.CalendarContract.Calendars
import androidx.core.content.contentValuesOf
import at.bitfire.davdroid.db.Service
import at.bitfire.davdroid.repository.DavCollectionRepository
import at.bitfire.davdroid.repository.DavServiceRepository
import at.bitfire.davdroid.resource.LocalAddressBookStore
import at.bitfire.davdroid.resource.LocalCalendarStore
import at.bitfire.davdroid.resource.LocalTaskList
import at.bitfire.davdroid.sync.TasksAppManager
import at.bitfire.ical4android.JtxCollection
import at.techbee.jtx.JtxContract
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntKey
import dagger.multibindings.IntoMap
import org.dmfs.tasks.contract.TaskContract.TaskLists
import javax.inject.Inject

/**
 * [at.bitfire.davdroid.sync.Syncer] now users collection IDs instead of URLs to match
 * local and remote (database) collections.
 *
 * This migration writes the database collection IDs to the local collections. If we wouldn't do that,
 * the syncer would not be able to find the correct local collection for a remote collection and
 * all local collections would be deleted and re-created.
 */
class AccountSettingsMigration20 @Inject constructor(
    @ApplicationContext context: Context,
    private val addressBookStore: LocalAddressBookStore,
    private val calendarStore: LocalCalendarStore,
    private val collectionRepository: DavCollectionRepository,
    private val serviceRepository: DavServiceRepository,
    private val tasksAppManager: TasksAppManager
): AccountSettingsMigration {

    val accountManager = AccountManager.get(context)

    override fun migrate(account: Account) {
        serviceRepository.getByAccountAndType(account.name, Service.TYPE_CARDDAV)?.let { cardDavService ->
            migrateAddressBooks(account, cardDavService.id)
        }

        serviceRepository.getByAccountAndType(account.name, Service.TYPE_CALDAV)?.let { calDavService ->
            migrateCalendars(account, calDavService.id)
            migrateTaskLists(account, calDavService.id)
        }
    }

    fun migrateAddressBooks(account: Account, cardDavServiceId: Long) {
        try {
            addressBookStore.acquireContentProvider()
        } catch (_: SecurityException) {
            // no contacts permission
            null
        }?.use { provider ->
            for (addressBook in addressBookStore.getAll(account, provider)) {
                val url = accountManager.getUserData(addressBook.addressBookAccount, ADDRESS_BOOK_USER_DATA_URL) ?: continue
                val collection = collectionRepository.getByServiceAndUrl(cardDavServiceId, url) ?: continue
                addressBook.dbCollectionId = collection.id
            }
        }
    }

    fun migrateCalendars(account: Account, calDavServiceId: Long) {
        try {
            calendarStore.acquireContentProvider()
        } catch (_: SecurityException) {
            // no contacts permission
            null
        }?.use { provider ->
            for (calendar in calendarStore.getAll(account, provider))
                provider.query(calendar.calendarSyncURI(), arrayOf(Calendars.NAME), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst())
                        cursor.getString(0)?.let { url ->
                            collectionRepository.getByServiceAndUrl(calDavServiceId, url)?.let { collection ->
                                calendar.update(contentValuesOf(
                                    Calendars._SYNC_ID to collection.id
                                ))
                            }
                        }
                }
        }
    }

    fun migrateTaskLists(account: Account, calDavServiceId: Long) {
        val taskListStore = tasksAppManager.getDataStore() ?: /* no tasks app */ return
        try {
            calendarStore.acquireContentProvider()
        } catch (_: SecurityException) {
            // no tasks permission
            null
        }?.use { provider ->
            for (taskList in taskListStore.getAll(account, provider)) {
                when (taskList) {
                    is LocalTaskList -> {       // tasks.org, OpenTasks
                        val url = taskList.syncId ?: continue
                        collectionRepository.getByServiceAndUrl(calDavServiceId, url)?.let { collection ->
                            taskList.update(contentValuesOf(
                                TaskLists._SYNC_ID to collection.id.toString()
                            ))
                        }
                    }
                    is JtxCollection<*> -> {    // jtxBoard
                        val url = taskList.url ?: continue
                        collectionRepository.getByServiceAndUrl(calDavServiceId, url)?.let { collection ->
                            taskList.update(contentValuesOf(
                                JtxContract.JtxCollection.SYNC_ID to collection.id
                            ))
                        }
                    }
                }
            }
        }
    }

    companion object {
        private const val ADDRESS_BOOK_USER_DATA_URL = "url"
    }

    @Module
    @InstallIn(SingletonComponent::class)
    abstract class AccountSettingsMigrationModule {
        @Binds @IntoMap
        @IntKey(20)
        abstract fun provide(impl: AccountSettingsMigration20): AccountSettingsMigration
    }

}