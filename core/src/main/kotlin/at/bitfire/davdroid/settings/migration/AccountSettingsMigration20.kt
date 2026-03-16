/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.settings.migration

import android.accounts.Account
import android.accounts.AccountManager
import android.content.Context
import android.provider.CalendarContract
import androidx.annotation.OpenForTesting
import androidx.core.content.contentValuesOf
import at.bitfire.davdroid.db.Service
import at.bitfire.davdroid.repository.DavCollectionRepository
import at.bitfire.davdroid.repository.DavServiceRepository
import at.bitfire.davdroid.resource.LocalAddressBookStore
import at.bitfire.davdroid.resource.LocalCalendarStore
import at.bitfire.davdroid.resource.LocalTaskList
import at.bitfire.davdroid.sync.TasksAppManager
import at.bitfire.ical4android.JtxCollection
import at.bitfire.synctools.storage.calendar.AndroidCalendarProvider
import at.techbee.jtx.JtxContract
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntKey
import dagger.multibindings.IntoMap
import kotlinx.coroutines.runBlocking
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
        runBlocking {
            serviceRepository.getByAccountAndType(account.name, Service.TYPE_CARDDAV)?.let { cardDavService ->
                migrateAddressBooks(account, cardDavService.id)
            }

            serviceRepository.getByAccountAndType(account.name, Service.TYPE_CALDAV)?.let { calDavService ->
                migrateCalendars(account, calDavService.id)
                migrateTaskLists(account, calDavService.id)
            }
        }
    }

    @OpenForTesting
    internal fun migrateAddressBooks(account: Account, cardDavServiceId: Long) {
        addressBookStore.acquireContentProvider()?.use { provider ->
            for (addressBook in addressBookStore.getAll(account, provider)) {
                val url = accountManager.getUserData(addressBook.addressBookAccount, ADDRESS_BOOK_USER_DATA_URL) ?: continue
                val collection = collectionRepository.getByServiceAndUrl(cardDavServiceId, url) ?: continue
                addressBook.dbCollectionId = collection.id
            }
        }
    }

    @OpenForTesting
    internal fun migrateCalendars(account: Account, calDavServiceId: Long) {
        calendarStore.acquireContentProvider()?.use { client ->
            val calendarProvider = AndroidCalendarProvider(account, client)
            // for each calendar, assign _SYNC_ID := ID if collection (identified by NAME field = URL)
            for (calendar in calendarProvider.findCalendars()) {
                val url = calendar.name ?: continue
                collectionRepository.getByServiceAndUrl(calDavServiceId, url)?.let { collection ->
                    calendar.update(contentValuesOf(
                        CalendarContract.Calendars._SYNC_ID to collection.id
                    ))
                }

            }
        }
    }

    @OpenForTesting
    internal fun migrateTaskLists(account: Account, calDavServiceId: Long) {
        val taskListStore = tasksAppManager.getDataStore() ?: /* no tasks app */ return
        taskListStore.acquireContentProvider()?.use { provider ->
            for (taskList in taskListStore.getAll(account, provider)) {
                when (taskList) {
                    is LocalTaskList -> {       // tasks.org, OpenTasks
                        val url = taskList.dmfsTaskList.syncId ?: continue
                        collectionRepository.getByServiceAndUrl(calDavServiceId, url)?.let { collection ->
                            taskList.dmfsTaskList.update(contentValuesOf(
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
        internal const val ADDRESS_BOOK_USER_DATA_URL = "url"
    }

    @Module
    @InstallIn(SingletonComponent::class)
    abstract class AccountSettingsMigrationModule {
        @Binds @IntoMap
        @IntKey(20)
        abstract fun provide(impl: AccountSettingsMigration20): AccountSettingsMigration
    }

}