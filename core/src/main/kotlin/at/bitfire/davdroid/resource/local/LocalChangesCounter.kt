/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.resource.local

import at.bitfire.davdroid.accounts.AccountId
import at.bitfire.davdroid.db.Collection
import at.bitfire.davdroid.di.qualifier.IoDispatcher
import at.bitfire.davdroid.sync.TasksAppManager
import dagger.Lazy
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.util.logging.Logger
import javax.annotation.CheckReturnValue
import javax.inject.Inject

/**
 * Counts local changes of a collection that haven't been uploaded to the server yet.
 */
class LocalChangesCounter @Inject constructor(
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    private val localAddressBookStore: Lazy<LocalAddressBookStore>,
    private val localCalendarStore: Lazy<LocalCalendarStore>,
    private val logger: Logger,
    private val tasksAppManager: Lazy<TasksAppManager>
) {

    /**
     * Counts unsynced local modifications and deletions of the local collection(s) of the given collection.
     *
     * Data stores that can't be accessed (for instance because of missing permissions) are skipped.
     *
     * @return number of unsynced local changes (0 if there are none or they couldn't be counted)
     */
    @CheckReturnValue
    suspend fun countUnsyncedChanges(accountId: AccountId, collection: Collection): Int =
        withContext(ioDispatcher) {
            val dataStores: List<LocalDataStore<*>> = when (collection.type) {
                Collection.TYPE_ADDRESSBOOK -> listOf(localAddressBookStore.get())
                Collection.TYPE_CALENDAR -> listOfNotNull(
                    localCalendarStore.get(),
                    tasksAppManager.get().getDataStore()
                )
                else -> emptyList()
            }

            dataStores.sumOf { dataStore ->
                countUnsyncedChanges(accountId, collection.id, dataStore)
            }
        }

    private fun countUnsyncedChanges(accountId: AccountId, collectionId: Long, dataStore: LocalDataStore<*>): Int {
        val client = dataStore.acquireLocalStorageClient()
        if (client == null) {
            logger.fine("Can't access ${dataStore.authority}, not counting unsynced changes")
            return 0
        }

        return client.use {
            dataStore.getByDbCollectionId(accountId, client, collectionId)?.let { localCollection ->
                localCollection.countModified() + localCollection.countDeleted()
            } ?: 0
        }
    }

}
